package com.neueda.trading.engine;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.neueda.events.OrderPlacedEvent;

/**
 * Writes a realistic full-day decision journal by running a simulated day through the real engine:
 * all symbols quoted every 120 s for 24 h, orders before, during and after market hours, and two
 * engine restarts after which the app resends the pending orders. The seed makes it reproducible.
 *
 * <p>It is how the golden journal {@code journals/2026-10-15-full-day.jsonl.gz} was made. Run it again only
 * after an intended change to the engine's decisions, then review the new decisions in the PR:
 * {@code mvn -q -pl execution-engine exec:java -Dexec.classpathScope=test
 * -Dexec.mainClass=com.neueda.trading.engine.JournalGenerator
 * -Dexec.args=$PWD/execution-engine/src/test/resources/journals/2026-10-15-full-day.jsonl.gz}.
 * An output name ending in {@code .gz} is written gzipped.
 */
public final class JournalGenerator {

    private static final Instant DAY = Instant.parse("2026-10-15T00:00:00Z");  // a Thursday
    private static final Instant OPEN = Instant.parse("2026-10-15T13:30:00Z");  // 09:30 New York
    private static final Instant CLOSE = Instant.parse("2026-10-15T20:00:00Z"); // 16:00 New York
    private static final Duration POLL = Duration.ofSeconds(120);
    // A deploy before the open, and a crash while trading; the engine is down for about a minute
    private static final List<Instant> RESTARTS =
        List.of(Instant.parse("2026-10-15T06:12:00Z"), Instant.parse("2026-10-15T15:47:00Z"));
    private static final Duration DOWNTIME = Duration.ofSeconds(70);

    private static final Map<String, Double> START_PRICES = new LinkedHashMap<>();

    static {
        String[] symbols = {"AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "TSLA", "AVGO", "BRK-B", "JPM",
            "LLY", "V", "XOM", "UNH", "MA", "JNJ", "PG", "HD", "COST", "ABBV", "WMT", "KO", "PEP", "BAC", "CVX",
            "SPY", "QQQ", "IWM", "DIA", "VTI", "EFA", "XLK", "XLE", "TLT", "IEF", "AGG", "LQD", "GLD", "SLV"};
        double[] prices = {341, 512, 186, 229, 248, 731, 412, 352, 498, 301, 803, 352, 118, 342, 571, 162,
            151, 389, 917, 228, 103, 69, 151, 51, 158, 668, 601, 247, 465, 331, 94, 286, 89, 90, 96, 99, 110,
            378, 48};
        for (int i = 0; i < symbols.length; i++) {
            START_PRICES.put(symbols[i], prices[i]);
        }
    }

    private final Random random = new Random(20261015);
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final MovableClock clock = new MovableClock();
    private final Map<String, Double> prices = new HashMap<>(START_PRICES);
    // Orders the app still has as PENDING, resent after every restart
    private final Map<UUID, OrderPlacedEvent> pending = new LinkedHashMap<>();
    private final TrackingJournal journal;
    private ExecutionEngine engine;

    private JournalGenerator(Path dir) {
        this.journal = new TrackingJournal(mapper, dir, pending);
    }

    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length > 0 ? args[0] : "2026-10-15-full-day.jsonl.gz").toAbsolutePath();
        Path dir = Files.createTempDirectory("journal");
        new JournalGenerator(dir).run();
        Path written = dir.resolve("2026-10-15.jsonl");
        if (output.getFileName().toString().endsWith(".gz")) {
            try (OutputStream gzip = new GZIPOutputStream(Files.newOutputStream(output))) {
                Files.copy(written, gzip);
            }
        } else {
            Files.copy(written, output, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("Wrote " + Files.readAllLines(written).size() + " lines to " + output);
    }

    private record Event(Instant at, int kind, Runnable action) {
    }

    private void run() {
        List<Event> events = new ArrayList<>();
        events.add(new Event(DAY, 0, this::startEngine));
        for (Instant restart : RESTARTS) {
            events.add(new Event(restart.plus(DOWNTIME), 0, this::startEngine));
            // The app resends what is still pending a second after the engine announces itself
            events.add(new Event(restart.plus(DOWNTIME).plusSeconds(1), 1, this::resendPending));
        }
        for (Instant poll = DAY.plusSeconds(30); poll.isBefore(DAY.plus(Duration.ofDays(1))); poll = poll.plus(POLL)) {
            Instant at = poll;
            if (RESTARTS.stream().noneMatch(r -> !at.isBefore(r) && at.isBefore(r.plus(DOWNTIME)))) {
                events.add(new Event(at, 2, () -> poll(at)));
            }
        }
        addOrders(events);
        events.sort(Comparator.comparing(Event::at).thenComparing(Event::kind));
        for (Event event : events) {
            clock.set(event.at());
            event.action().run();
        }
    }

    private void addOrders(List<Event> events) {
        List<String> symbols = new ArrayList<>(START_PRICES.keySet());
        // Before the open: queued, then priced at the open
        for (int i = 0; i < 6; i++) {
            events.add(order(between(DAY.plusSeconds(3600), OPEN), symbols, null));
        }
        // While trading
        for (int i = 0; i < 100; i++) {
            events.add(order(between(OPEN.plusSeconds(60), CLOSE), symbols, null));
        }
        // After the close: still pending at the end of the day
        for (int i = 0; i < 8; i++) {
            events.add(order(between(CLOSE.plusSeconds(60), DAY.plus(Duration.ofHours(23))), symbols, null));
        }
        // Invalid side, as a broken client might send
        events.add(order(Instant.parse("2026-10-15T14:22:13Z"), symbols, "HOLD"));
        events.add(order(Instant.parse("2026-10-15T18:05:41Z"), symbols, "HOLD"));
        // Right after the trading-hours restart, before the first quote: no fresh quote yet
        Instant afterRestart = RESTARTS.get(1).plus(DOWNTIME).plusSeconds(12);
        events.add(new Event(afterRestart, 3, () -> place(newOrder("NVDA", "BUY", "MARKET", 15, null))));
    }

    private Event order(Instant at, List<String> symbols, String side) {
        String symbol = symbols.get(random.nextInt(symbols.size()));
        String orderSide = side != null ? side : (random.nextInt(3) == 0 ? "SELL" : "BUY");
        boolean limit = random.nextBoolean();
        int quantity = 1 + random.nextInt(100);
        double offset = random.nextDouble() * 0.008;                  // up to 0.8% away from the price
        boolean buy = !"SELL".equals(orderSide);
        return new Event(at, 3, () -> {
            BigDecimal limitPrice = limit
                ? money(prices.get(symbol) * (buy ? 1 - offset : 1 + offset))
                : null;
            place(newOrder(symbol, orderSide, limit ? "LIMIT" : "MARKET", quantity, limitPrice));
        });
    }

    private OrderPlacedEvent newOrder(String symbol, String side, String type, int quantity, BigDecimal limit) {
        String account = "ACC00" + (1 + random.nextInt(5));
        return new OrderPlacedEvent(new UUID(random.nextLong(), random.nextLong()), account, symbol, side,
            type, quantity, limit, new UUID(random.nextLong(), random.nextLong()).toString());
    }

    private void place(OrderPlacedEvent order) {
        pending.put(order.orderId(), order);
        engine.handle(order);
    }

    private void startEngine() {
        MarketDataProperties props = new MarketDataProperties(
            new ArrayList<>(START_PRICES.keySet()), 120, 25, 2000, 600, "http://alpaca", "", "");
        engine = new ExecutionEngine(mapper, new SilentProducer(mapper), new QuoteCache(), props, journal, clock, true);
        engine.announceStarted();
    }

    private void resendPending() {
        for (OrderPlacedEvent order : List.copyOf(pending.values())) {
            engine.handle(order);
        }
    }

    private void poll(Instant at) {
        boolean open = MarketHours.isOpen(at);
        int i = 0;
        for (String symbol : START_PRICES.keySet()) {
            if (open) {
                prices.put(symbol, prices.get(symbol) * Math.exp(random.nextGaussian() * 0.0015));
            }
            double price = prices.get(symbol);
            double halfSpread = Math.max(0.01, price * 0.0002);
            BigDecimal bid = money(price - halfSpread);
            // Outside market hours IEX often has no offer; Alpaca then returns an ask of 0
            BigDecimal ask = !open && (symbol.hashCode() & 3) == 0 ? BigDecimal.ZERO : money(price + halfSpread);
            // The engine receives each quote a few milliseconds after the poller fetched it
            clock.set(at.plusMillis(40 + i++));
            engine.accept(new Quote(symbol, bid, ask, at));
        }
    }

    private Instant between(Instant from, Instant to) {
        return from.plusSeconds((long) (random.nextDouble() * Duration.between(from, to).getSeconds()));
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Writes the real journal, and forgets an order once it is filled or rejected, as the app would. */
    private static final class TrackingJournal extends DecisionJournal {

        private final Map<UUID, OrderPlacedEvent> pending;

        TrackingJournal(ObjectMapper mapper, Path dir, Map<UUID, OrderPlacedEvent> pending) {
            super(mapper, dir.toString(), true);
            this.pending = pending;
        }

        @Override
        public synchronized void record(Instant time, String type, Map<String, Object> fields) {
            super.record(time, type, fields);
            Object decision = fields.get("decision");
            if (JournalTypes.FILL.equals(decision) || JournalTypes.REJECT.equals(decision)) {
                pending.remove((UUID) fields.get("orderId"));
            }
        }
    }

    private static final class SilentProducer extends EventProducerService {

        SilentProducer(ObjectMapper mapper) {
            super(null, mapper);
        }

        @Override
        public void publishEvent(String topic, String key, String eventType, String source, Object payload) {
            // the generator only needs the journal
        }
    }

    private static final class MovableClock extends Clock {

        private Instant now = Instant.EPOCH;

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
