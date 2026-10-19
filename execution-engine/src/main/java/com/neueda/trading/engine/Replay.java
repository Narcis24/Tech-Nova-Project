package com.neueda.trading.engine;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.neueda.events.OrderPlacedEvent;

/**
 * Re-runs a decision journal through a fresh engine and compares its decisions with the
 * recorded ones. No Kafka, database or network: the engine's sender does nothing, its journal
 * collects the new decisions in memory, and its clock is set to each line's recorded time.
 *
 * <p>Run: {@code java ... com.neueda.trading.engine.Replay <journal.jsonl>}; exit code 0 when
 * every decision matches, 1 otherwise.
 */
public final class Replay {

    // Defaults for journals written before ENGINE_STARTED carried the settings
    private static final int DEFAULT_MAX_QUOTE_AGE_SECONDS = 600;
    private static final boolean DEFAULT_ENFORCE_MARKET_HOURS = true;

    /** One decision; seq is the journal line it was recorded on (0 for a replayed one). */
    record Decision(long seq, String orderId, String decision, BigDecimal price, String reason) {

        boolean sameAs(Decision other) {
            return orderId.equals(other.orderId)
                && decision.equals(other.decision)
                && (price == null ? other.price == null : other.price != null && price.compareTo(other.price) == 0)
                && Objects.equals(reason, other.reason);
        }

        @Override
        public String toString() {
            return orderId + " " + decision
                + (price != null ? " " + price.toPlainString() : "")
                + (reason != null ? " (" + reason + ")" : "");
        }
    }

    record Result(int runs, int orders, int quotes, List<Decision> recorded, List<String> differences) {

        boolean matches() {
            return differences.isEmpty();
        }
    }

    private final ObjectMapper mapper;
    private final ReplayClock clock = new ReplayClock();
    private final List<Decision> replayed = new ArrayList<>();

    Replay(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static void main(String[] args) {
        System.exit(execute(args, System.out));
    }

    /** Runs a replay and prints the report; returns the exit code. */
    static int execute(String[] args, PrintStream out) {
        if (args.length != 1) {
            out.println("Usage: Replay <journal.jsonl>");
            return 1;
        }
        // The engine logs every order and decision; the report is what matters here
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
        Path journal = Path.of(args[0]);
        try {
            // Prices read exactly as written (100.50, not 100.5), for the report
            ObjectMapper mapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
            Result result = new Replay(mapper).run(journal);
            print(journal, result, out);
            return result.matches() ? 0 : 1;
        } catch (IOException | JacksonException | IllegalArgumentException e) {
            out.println("Could not replay " + journal + ": " + e.getMessage());
            return 1;
        }
    }

    Result run(Path journal) throws IOException {
        List<Decision> recorded = new ArrayList<>();
        ExecutionEngine engine = null;
        int runs = 0;
        int orders = 0;
        int quotes = 0;
        for (String text : Files.readAllLines(journal)) {
            if (text.isBlank()) {
                continue;
            }
            JsonNode line = mapper.readTree(text);
            clock.set(Instant.parse(line.get("time").asString()));
            switch (line.get("type").asString()) {
                case JournalTypes.ENGINE_STARTED -> {
                    // A restart loses the cached quotes and waiting orders, so the replay starts fresh too
                    engine = freshEngine(line);
                    runs++;
                }
                case JournalTypes.ORDER_RECEIVED -> {
                    engine = engine != null ? engine : freshEngine(line);
                    engine.handle(mapper.treeToValue(line.get("order"), OrderPlacedEvent.class));
                    orders++;
                }
                case JournalTypes.QUOTE_RECEIVED -> {
                    engine = engine != null ? engine : freshEngine(line);
                    engine.accept(mapper.treeToValue(line.get("quote"), Quote.class));
                    quotes++;
                }
                case JournalTypes.DECISION -> recorded.add(new Decision(
                    line.get("seq").asLong(),
                    line.get("orderId").asString(),
                    line.get("decision").asString(),
                    line.has("price") ? line.get("price").decimalValue() : null,
                    line.has("reason") ? line.get("reason").asString() : null));
                default -> throw new IllegalArgumentException("Unknown journal line type: " + line.get("type"));
            }
        }
        if (runs == 0 && orders == 0 && quotes == 0) {
            throw new IllegalArgumentException("the journal has no engine runs, orders or quotes");
        }
        return new Result(runs, orders, quotes, recorded, compare(recorded, replayed));
    }

    private ExecutionEngine freshEngine(JsonNode line) {
        int maxQuoteAge = line.has("maxQuoteAgeSeconds")
            ? line.get("maxQuoteAgeSeconds").asInt() : DEFAULT_MAX_QUOTE_AGE_SECONDS;
        boolean enforceHours = line.has("enforceMarketHours")
            ? line.get("enforceMarketHours").asBoolean() : DEFAULT_ENFORCE_MARKET_HOURS;
        MarketDataProperties props = new MarketDataProperties(
            List.of("REPLAY"), 120, 25, 2000, maxQuoteAge, "http://replay", "", "");
        return new ExecutionEngine(mapper, new SilentProducer(mapper), new QuoteCache(), props,
            new CollectingJournal(mapper, replayed), clock, enforceHours);
    }

    /** Pairs recorded and replayed decisions in order and describes every mismatch. */
    private static List<String> compare(List<Decision> recorded, List<Decision> replayed) {
        List<String> differences = new ArrayList<>();
        for (int i = 0; i < Math.max(recorded.size(), replayed.size()); i++) {
            Decision was = i < recorded.size() ? recorded.get(i) : null;
            Decision now = i < replayed.size() ? replayed.get(i) : null;
            if (was != null && now != null && was.sameAs(now)) {
                continue;
            }
            String where = was != null ? "seq " + was.seq() : "after the last recorded decision";
            differences.add(where + ": recorded " + (was != null ? was : "nothing")
                + ", replay decided " + (now != null ? now : "nothing"));
        }
        return differences;
    }

    private static void print(Path journal, Result result, PrintStream out) {
        out.println("Replay of " + journal);
        out.printf("  engine runs %d, orders %d, quotes %d, decisions %d%n",
            result.runs(), result.orders(), result.quotes(), result.recorded().size());
        if (result.matches()) {
            out.println("  all decisions match");
            return;
        }
        out.println("  " + result.differences().size() + " difference(s):");
        result.differences().forEach(d -> out.println("    " + d));
    }

    /** The engine's sender during a replay: decisions are collected by the journal, nothing is sent. */
    private static final class SilentProducer extends EventProducerService {

        SilentProducer(ObjectMapper mapper) {
            super(null, mapper);
        }

        @Override
        public void publishEvent(String topic, String key, String eventType, String source, Object payload) {
            // nothing to send during a replay
        }
    }

    /** Keeps the replayed decisions in memory instead of writing a file. */
    private static final class CollectingJournal extends DecisionJournal {

        private final List<Decision> decisions;

        CollectingJournal(ObjectMapper mapper, List<Decision> decisions) {
            super(mapper, ".", false);
            this.decisions = decisions;
        }

        @Override
        public synchronized void record(Instant time, String type, Map<String, Object> fields) {
            if (JournalTypes.DECISION.equals(type)) {
                decisions.add(new Decision(0,
                    String.valueOf(fields.get("orderId")),
                    (String) fields.get("decision"),
                    (BigDecimal) fields.get("price"),
                    (String) fields.get("reason")));
            }
        }
    }

    /** A clock the replay moves to each journal line's time. */
    private static final class ReplayClock extends Clock {

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
