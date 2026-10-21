package com.neueda.trading.engine;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.neueda.events.OrderPlacedEvent;

/**
 * Writes a decision journal of a busy retail-broker day, modelled on Fidelity's published statistics and
 * scaled to 1% of their volume. Synthetic: no real orders are public; only the totals and mixes are.
 * Like {@link JournalGenerator} it runs the day through the real engine, so every decision is one the engine
 * really makes.
 *
 * <ul>
 *   <li>44,000 orders: 1% of the 4.4 million daily average trades in Fidelity's 2025 annual report.</li>
 *   <li>Order types from Fidelity's Rule 606 routing report: 49.7% market, 9.6% marketable limit, 36.8%
 *       non-marketable limit. The 3.9% "other" (stops and the like) is left out; the engine has no such type.</li>
 *   <li>Sizes: about $2,500 per order typically, but with a long tail, so orders of 100 to 1,999 shares
 *       (the range Fidelity's Rule 605 statistics cover) are common and a few are larger.</li>
 *   <li>Busy after the open and before the close, quiet at midday; 3% before the open, 2% after the close.</li>
 *   <li>Our 39 instruments, from their closes on 2026-10-13 in price_data, weighted by real volume and
 *       retail favourites; 55% buys; 20,000 accounts, a few very active; many waiting limits cancelled.</li>
 * </ul>
 *
 * <p>Not in git (too big, and not a golden journal): it goes to {@code shadow-journals/}, which the Shadow Gate
 * replays when copied to the Jenkins machine. From the project root:
 * {@code mvn -q -pl execution-engine exec:java -Dexec.classpathScope=test
 * -Dexec.mainClass=com.neueda.trading.engine.BrokerDayGenerator
 * -Dexec.args=$PWD/shadow-journals/2026-10-16-broker-day.jsonl.gz}
 */
public final class BrokerDayGenerator {

    private static final String DAY_NAME = "2026-10-16";                       // a Friday
    private static final Instant DAY = Instant.parse(DAY_NAME + "T00:00:00Z");
    private static final Instant PRE_MARKET = Instant.parse(DAY_NAME + "T08:00:00Z");   // 04:00 New York
    private static final Instant OPEN = Instant.parse(DAY_NAME + "T13:30:00Z");         // 09:30 New York
    private static final Instant CLOSE = Instant.parse(DAY_NAME + "T20:00:00Z");        // 16:00 New York
    private static final Instant AFTER_HOURS_END = Instant.parse(DAY_NAME + "T23:30:00Z");
    private static final Duration POLL = Duration.ofSeconds(120);
    // A deploy before the open; the engine is down for a minute and the app resends what is pending
    private static final Instant RESTART = Instant.parse(DAY_NAME + "T10:15:00Z");
    private static final Duration DOWNTIME = Duration.ofSeconds(60);

    private static final int ORDERS = 44_000;
    private static final int ACCOUNTS = 20_000;
    private static final double PRE_MARKET_SHARE = 0.03;
    private static final double AFTER_HOURS_SHARE = 0.02;
    // Rule 606 mix without "other": market, marketable limit, non-marketable limit
    private static final double MARKET = 49.7;
    private static final double MARKETABLE_LIMIT = 9.6;
    private static final double NON_MARKETABLE_LIMIT = 36.8;
    private static final double BUY_SHARE = 0.55;
    private static final double CANCEL_SHARE = 0.45;              // of the limits that wait during the day
    private static final double MEDIAN_NOTIONAL = 2_500;
    private static final double NOTIONAL_SIGMA = 1.3;
    private static final int MAX_QUANTITY = 5_000;
    // Orders per half hour from 09:30 to 16:00 New York: busy at the open and the close
    private static final double[] HALF_HOURS = {13, 9, 7.5, 6.5, 5.5, 5, 4.8, 4.8, 5, 5.5, 6, 7.5, 12};

    private record Instrument(String symbol, double close, long volume, double favourite, double volatility) {
    }

    // Closes and volumes of 2026-10-13 from price_data; favourite: how much more retail trades it than volume says
    private static final List<Instrument> INSTRUMENTS = List.of(
        new Instrument("NVDA", 228.52, 83_846_800, 3, 0.0025),
        new Instrument("TSLA", 375.23, 18_349_700, 5, 0.0030),
        new Instrument("AAPL", 341.27, 38_946_900, 2, 0.0013),
        new Instrument("SPY", 778.01, 25_787_100, 2, 0.0008),
        new Instrument("QQQ", 743.57, 28_608_300, 2, 0.0010),
        new Instrument("AMZN", 258.58, 25_227_400, 1.5, 0.0016),
        new Instrument("META", 762.50, 22_679_300, 1.5, 0.0018),
        new Instrument("MSFT", 543.45, 23_824_500, 1.3, 0.0012),
        new Instrument("GOOGL", 353.78, 23_148_200, 1.2, 0.0015),
        new Instrument("AVGO", 357.31, 17_241_200, 1.3, 0.0020),
        new Instrument("BAC", 53.96, 44_949_900, 1, 0.0013),
        new Instrument("IWM", 276.70, 27_724_000, 1, 0.0011),
        new Instrument("TLT", 78.37, 29_573_300, 1, 0.0008),
        new Instrument("XLE", 67.20, 27_488_600, 1, 0.0012),
        new Instrument("LQD", 102.55, 26_122_300, 0.6, 0.0004),
        new Instrument("SLV", 53.49, 17_502_400, 1.5, 0.0018),
        new Instrument("WMT", 112.28, 17_673_100, 1, 0.0010),
        new Instrument("JNJ", 248.61, 16_620_900, 1, 0.0009),
        new Instrument("KO", 90.11, 15_313_000, 1, 0.0008),
        new Instrument("JPM", 331.43, 13_753_800, 1, 0.0012),
        new Instrument("IEF", 89.54, 12_778_800, 0.6, 0.0004),
        new Instrument("UNH", 377.76, 12_639_500, 1, 0.0015),
        new Instrument("PEP", 127.30, 11_776_800, 1, 0.0009),
        new Instrument("EFA", 103.47, 11_707_700, 0.8, 0.0008),
        new Instrument("XOM", 174.08, 11_505_200, 1, 0.0012),
        new Instrument("AGG", 94.71, 9_543_100, 0.6, 0.0003),
        new Instrument("V", 396.06, 7_476_700, 1, 0.0010),
        new Instrument("CVX", 217.73, 7_432_300, 1, 0.0012),
        new Instrument("XLK", 196.28, 6_749_900, 1, 0.0012),
        new Instrument("ABBV", 274.36, 6_408_500, 1, 0.0011),
        new Instrument("PG", 153.04, 6_038_000, 1, 0.0008),
        new Instrument("GLD", 381.67, 5_703_700, 1.5, 0.0010),
        new Instrument("BRK-B", 520.59, 4_765_600, 1.2, 0.0008),
        new Instrument("HD", 293.92, 4_520_800, 1, 0.0011),
        new Instrument("MA", 604.80, 3_710_300, 1, 0.0010),
        new Instrument("DIA", 516.21, 3_376_200, 1, 0.0008),
        new Instrument("VTI", 381.32, 2_458_800, 2, 0.0008),
        new Instrument("LLY", 1166.51, 1_843_200, 1.5, 0.0016),
        new Instrument("COST", 954.63, 1_422_300, 1.5, 0.0010));

    private final Random random = new Random(20261016);
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final JournalGenerator.MovableClock clock = new JournalGenerator.MovableClock();
    private final Map<String, Double> prices = new HashMap<>();
    private final double[] cumulativeWeights = new double[INSTRUMENTS.size()];
    // Orders the app still has as PENDING: resent after the restart, and the only ones a holder can cancel
    private final Map<UUID, OrderPlacedEvent> pending = new LinkedHashMap<>();
    private final JournalGenerator.TrackingJournal journal;
    private ExecutionEngine engine;

    private BrokerDayGenerator(Path dir) {
        this.journal = new JournalGenerator.TrackingJournal(mapper, dir, pending);
        double total = 0;
        for (int i = 0; i < INSTRUMENTS.size(); i++) {
            Instrument instrument = INSTRUMENTS.get(i);
            prices.put(instrument.symbol(), instrument.close());
            total += Math.pow(instrument.volume(), 0.7) * instrument.favourite();
            cumulativeWeights[i] = total;
        }
    }

    // A command-line tool: what it prints is its result
    @SuppressWarnings("java:S106")
    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length > 0 ? args[0] : "shadow-journals/" + DAY_NAME + "-broker-day.jsonl.gz")
            .toAbsolutePath();
        Path dir = Files.createTempDirectory("journal");
        new BrokerDayGenerator(dir).run();
        Path written = dir.resolve(DAY_NAME + ".jsonl");
        Files.createDirectories(output.getParent());
        try (OutputStream gzip = new GZIPOutputStream(Files.newOutputStream(output))) {
            Files.copy(written, gzip);
        }
        JournalExplorer.DaySummary day = new JournalExplorer(dir.toString()).summary(DAY_NAME).orElseThrow();
        long big = day.orderRows().stream().filter(r -> r.quantity() != null && r.quantity() >= 100).count();
        long market = day.orderRows().stream().filter(r -> "MARKET".equals(r.orderType())).count();
        System.out.printf("Wrote %s (%,d KB)%n", output, Files.size(output) / 1024);
        System.out.printf("  orders %,d (market %,d, limit %,d), 100+ shares %,d%n",
            day.orders(), market, day.orders() - market, big);
        System.out.printf("  fills %,d, rejects %,d, waits %,d, queued %,d, cancels %,d, quotes %,d%n",
            day.fills(), day.rejects(), day.waits(), day.queued(), day.cancels(), day.quotes());
    }

    /** kind orders events at the same instant: engine start, resend, quotes, orders, cancels; then by number. */
    private record Event(Instant at, int kind, long number, Runnable action) {
    }

    private final PriorityQueue<Event> events = new PriorityQueue<>(
        Comparator.comparing(Event::at).thenComparingInt(Event::kind).thenComparingLong(Event::number));
    private long eventNumber;

    private void add(Instant at, int kind, Runnable action) {
        events.add(new Event(at, kind, eventNumber++, action));
    }

    private void run() {
        add(DAY, 0, this::startEngine);
        add(RESTART.plus(DOWNTIME), 0, this::startEngine);
        add(RESTART.plus(DOWNTIME).plusSeconds(1), 1, this::resendPending);
        for (Instant poll = DAY.plusSeconds(30); poll.isBefore(DAY.plus(Duration.ofDays(1))); poll = poll.plus(POLL)) {
            Instant at = poll;
            if (at.isBefore(RESTART) || !at.isBefore(RESTART.plus(DOWNTIME))) {
                add(at, 2, () -> poll(at));
            }
        }
        for (int i = 0; i < ORDERS; i++) {
            addOrder(orderTime());
        }
        // Placing an order may add its cancel to the queue, so the queue is read until empty
        while (!events.isEmpty()) {
            Event event = events.poll();
            clock.set(event.at());
            event.action().run();
        }
    }

    private Instant orderTime() {
        double draw = random.nextDouble();
        if (draw < PRE_MARKET_SHARE) {
            return between(PRE_MARKET, OPEN);
        }
        if (draw < PRE_MARKET_SHARE + AFTER_HOURS_SHARE) {
            return between(CLOSE.plusSeconds(1), AFTER_HOURS_END);
        }
        int halfHour = pick(HALF_HOURS);
        Instant from = OPEN.plus(Duration.ofMinutes(30L * halfHour));
        // Not in the very first second, before the first quote after the open
        return between(halfHour == 0 ? from.plusSeconds(31) : from, from.plus(Duration.ofMinutes(30)));
    }

    private void addOrder(Instant at) {
        Instrument instrument = instrument();
        boolean buy = random.nextDouble() < BUY_SHARE;
        int type = pick(new double[] {MARKET, MARKETABLE_LIMIT, NON_MARKETABLE_LIMIT});
        double notional = MEDIAN_NOTIONAL * Math.exp(random.nextGaussian() * NOTIONAL_SIGMA);
        double distance = random.nextDouble() < 0.6
            ? 0.001 + random.nextDouble() * 0.004                       // close to the price: often fills later
            : 0.005 + random.nextDouble() * 0.025;                      // far away: usually waits all day
        double through = random.nextDouble() * 0.003;                    // a marketable limit's margin
        add(at, 3, () -> {
            double price = prices.get(instrument.symbol());
            double halfSpread = halfSpread(price);
            int quantity = (int) Math.clamp(Math.round(notional / price), 1, MAX_QUANTITY);
            BigDecimal limit = switch (type) {
                case 1 -> money(buy ? (price + halfSpread) * (1 + through) : (price - halfSpread) * (1 - through));
                case 2 -> money(buy ? (price - halfSpread) * (1 - distance) : (price + halfSpread) * (1 + distance));
                default -> null;
            };
            OrderPlacedEvent order = newOrder(instrument.symbol(), buy ? "BUY" : "SELL",
                limit != null ? "LIMIT" : "MARKET", quantity, limit);
            pending.put(order.orderId(), order);
            engine.handle(order);
            maybeCancelLater(order, at);
        });
    }

    /** Some waiting limits are cancelled by their holder a while later, if still waiting then. */
    private void maybeCancelLater(OrderPlacedEvent order, Instant placedAt) {
        boolean waiting = pending.containsKey(order.orderId()) && "LIMIT".equals(order.orderType());
        if (!waiting || !MarketHours.isOpen(placedAt) || random.nextDouble() >= CANCEL_SHARE) {
            return;
        }
        // Typically about 40 minutes later; one still waiting at the close stays open
        Instant at = placedAt.plusSeconds(Math.round(random.nextExponential() * 40 * 60));
        if (at.isBefore(CLOSE)) {
            add(at, 4, () -> {
                if (pending.remove(order.orderId()) != null) {
                    engine.cancel(order.orderId());
                }
            });
        }
    }

    private Instrument instrument() {
        double draw = random.nextDouble() * cumulativeWeights[cumulativeWeights.length - 1];
        for (int i = 0; i < cumulativeWeights.length; i++) {
            if (draw < cumulativeWeights[i]) {
                return INSTRUMENTS.get(i);
            }
        }
        return INSTRUMENTS.get(INSTRUMENTS.size() - 1);
    }

    private OrderPlacedEvent newOrder(String symbol, String side, String type, int quantity, BigDecimal limit) {
        // Nested draws favour low numbers, so a few accounts place many orders and most place one or two
        int account = random.nextInt(random.nextInt(random.nextInt(ACCOUNTS) + 1) + 1);
        return new OrderPlacedEvent(new UUID(random.nextLong(), random.nextLong()), "ACC%05d".formatted(account),
            symbol, side, type, quantity, limit, new UUID(random.nextLong(), random.nextLong()).toString());
    }

    private void startEngine() {
        MarketDataProperties props = new MarketDataProperties(
            INSTRUMENTS.stream().map(Instrument::symbol).toList(), 120, 25, 2000, 600, "http://alpaca", "", "");
        engine = new ExecutionEngine(mapper, new JournalGenerator.SilentProducer(mapper), new QuoteCache(), props,
            journal, clock, true);
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
        for (Instrument instrument : INSTRUMENTS) {
            String symbol = instrument.symbol();
            if (open) {
                prices.put(symbol, prices.get(symbol) * Math.exp(random.nextGaussian() * instrument.volatility()));
            }
            double price = prices.get(symbol);
            double halfSpread = halfSpread(price);
            clock.set(at.plusMillis(40 + i++));
            engine.accept(new Quote(symbol, money(price - halfSpread), money(price + halfSpread), at));
        }
    }

    private static double halfSpread(double price) {
        return Math.max(0.01, price * 0.0002);
    }

    private int pick(double[] weights) {
        double total = 0;
        for (double weight : weights) {
            total += weight;
        }
        double draw = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            draw -= weights[i];
            if (draw < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    private Instant between(Instant from, Instant to) {
        return from.plusMillis(random.nextLong(Duration.between(from, to).toMillis()));
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
