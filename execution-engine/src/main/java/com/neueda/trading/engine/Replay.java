package com.neueda.trading.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

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
 * <p>Run: {@code java ... com.neueda.trading.engine.Replay [--report <report.json>] <journal or folder>...};
 * a folder means every {@code .jsonl} and {@code .jsonl.gz} journal in it. Exit code 0 when every decision
 * of every journal matches, 1 otherwise. The report has each difference with the order and the last quote
 * behind it, so it can be analysed later (Jenkins archives it).
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

    /** A recorded decision that came out differently, with the order and the last quote for its symbol before it. */
    record Difference(long seq, Decision recorded, Decision replayed, JsonNode order, JsonNode lastQuote) {

        @Override
        public String toString() {
            String where = recorded != null ? "seq " + seq : "not recorded";
            return where + ": recorded " + (recorded != null ? recorded : "nothing")
                + ", replay decided " + (replayed != null ? replayed : "nothing");
        }
    }

    record Result(int runs, int orders, int quotes, List<Decision> recorded, List<Difference> differences) {

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

    /** Replays every journal given, prints the results and writes the report; returns the exit code. */
    static int execute(String[] args, PrintStream out) {
        Path report = null;
        List<Path> inputs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("--report".equals(args[i]) && i + 1 < args.length) {
                report = Path.of(args[++i]);
            } else {
                inputs.add(Path.of(args[i]));
            }
        }
        if (inputs.isEmpty()) {
            out.println("Usage: Replay [--report <report.json>] <journal or folder>...");
            return 1;
        }
        // The engine logs every order and decision; the report is what matters here
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
        // Prices read exactly as written (100.50, not 100.5), for the report
        ObjectMapper mapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

        boolean allMatch = true;
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Path journal : journals(inputs, out)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("file", journal.toString());
            try {
                Result result = new Replay(mapper).run(journal);
                print(journal, result, out);
                allMatch &= result.matches();
                entry.put("runs", result.runs());
                entry.put("orders", result.orders());
                entry.put("quotes", result.quotes());
                entry.put("decisions", result.recorded().size());
                entry.put("differences", result.differences().stream().map(Replay::reportEntry).toList());
            } catch (IOException | JacksonException | IllegalArgumentException e) {
                out.println("Could not replay " + journal + ": " + e.getMessage());
                allMatch = false;
                entry.put("error", String.valueOf(e.getMessage()));
            }
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            out.println("Could not replay: no journals found in " + inputs);
            allMatch = false;
        }
        if (report != null && !writeReport(mapper, report, allMatch, entries, out)) {
            return 1;
        }
        return allMatch ? 0 : 1;
    }

    /** The journals to replay: files as given, and every journal inside a folder, sorted by name. */
    private static List<Path> journals(List<Path> inputs, PrintStream out) {
        List<Path> journals = new ArrayList<>();
        for (Path input : inputs) {
            if (!Files.isDirectory(input)) {
                journals.add(input);
                continue;
            }
            try (Stream<Path> files = Files.list(input)) {
                files.filter(f -> f.getFileName().toString().matches(".*\\.jsonl(\\.gz)?")).sorted().forEach(journals::add);
            } catch (IOException e) {
                out.println("Could not list " + input + ": " + e.getMessage());
            }
        }
        return journals;
    }

    private static Map<String, Object> reportEntry(Difference d) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("seq", d.recorded() != null ? d.seq() : null);
        entry.put("orderId", d.recorded() != null ? d.recorded().orderId() : d.replayed().orderId());
        entry.put("recorded", d.recorded() != null ? d.recorded().toString() : null);
        entry.put("replayed", d.replayed() != null ? d.replayed().toString() : null);
        entry.put("order", d.order());
        entry.put("lastQuote", d.lastQuote());
        return entry;
    }

    private static boolean writeReport(ObjectMapper mapper, Path report, boolean matches,
            List<Map<String, Object>> journals, PrintStream out) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("matches", matches);
        content.put("journals", journals);
        try {
            if (report.toAbsolutePath().getParent() != null) {
                Files.createDirectories(report.toAbsolutePath().getParent());
            }
            Files.writeString(report, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(content));
            out.println("Report written to " + report);
            return true;
        } catch (IOException | JacksonException e) {
            out.println("Could not write the report " + report + ": " + e.getMessage());
            return false;
        }
    }

    Result run(Path journal) throws IOException {
        List<Decision> recorded = new ArrayList<>();
        // For the report: each recorded decision's order, and the last quote for its symbol before it
        Map<String, JsonNode> ordersById = new HashMap<>();
        Map<String, JsonNode> lastQuotes = new HashMap<>();
        List<JsonNode[]> context = new ArrayList<>();
        ExecutionEngine engine = null;
        int runs = 0;
        int orders = 0;
        int quotes = 0;
        for (String text : readLines(journal)) {
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
                    ordersById.put(line.get("order").get("orderId").asString(), line.get("order"));
                    orders++;
                }
                case JournalTypes.QUOTE_RECEIVED -> {
                    engine = engine != null ? engine : freshEngine(line);
                    engine.accept(mapper.treeToValue(line.get("quote"), Quote.class));
                    lastQuotes.put(line.get("quote").get("symbol").asString(), line.get("quote"));
                    quotes++;
                }
                case JournalTypes.DECISION -> {
                    String orderId = line.get("orderId").asString();
                    recorded.add(new Decision(
                        line.get("seq").asLong(),
                        orderId,
                        line.get("decision").asString(),
                        line.has("price") ? line.get("price").decimalValue() : null,
                        line.has("reason") ? line.get("reason").asString() : null));
                    JsonNode order = ordersById.get(orderId);
                    context.add(new JsonNode[] {order,
                        order != null ? lastQuotes.get(order.get("symbol").asString()) : null});
                }
                default -> throw new IllegalArgumentException("Unknown journal line type: " + line.get("type"));
            }
        }
        if (runs == 0 && orders == 0 && quotes == 0) {
            throw new IllegalArgumentException("the journal has no engine runs, orders or quotes");
        }
        return new Result(runs, orders, quotes, recorded, compare(recorded, context, replayed));
    }

    /** The journal's lines; a {@code .gz} journal is unzipped while it is read. */
    static List<String> readLines(Path journal) throws IOException {
        if (!journal.getFileName().toString().endsWith(".gz")) {
            return Files.readAllLines(journal);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(journal)), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
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

    /**
     * Compares each order's decisions on their own: the 1st recorded decision for an order with its 1st
     * replayed one, and so on (an order can have several, e.g. WAIT then FILL). Two orders decided in a
     * different order, or one missing decision, then show as just that, not as a shift of everything after.
     */
    private static List<Difference> compare(List<Decision> recorded, List<JsonNode[]> context, List<Decision> replayed) {
        Map<String, List<Integer>> recordedByOrder = byOrder(recorded);
        Map<String, List<Integer>> replayedByOrder = byOrder(replayed);
        // Recorded orders in journal order, then any order only the replay decided
        Set<String> orderIds = new LinkedHashSet<>(recordedByOrder.keySet());
        orderIds.addAll(replayedByOrder.keySet());

        List<Difference> differences = new ArrayList<>();
        for (String orderId : orderIds) {
            List<Integer> was = recordedByOrder.getOrDefault(orderId, List.of());
            List<Integer> now = replayedByOrder.getOrDefault(orderId, List.of());
            for (int k = 0; k < Math.max(was.size(), now.size()); k++) {
                Decision recordedDecision = k < was.size() ? recorded.get(was.get(k)) : null;
                Decision replayedDecision = k < now.size() ? replayed.get(now.get(k)) : null;
                if (recordedDecision != null && replayedDecision != null && recordedDecision.sameAs(replayedDecision)) {
                    continue;
                }
                JsonNode[] around = recordedDecision != null ? context.get(was.get(k)) : new JsonNode[2];
                differences.add(new Difference(recordedDecision != null ? recordedDecision.seq() : 0,
                    recordedDecision, replayedDecision, around[0], around[1]));
            }
        }
        return differences;
    }

    /** The positions of each order's decisions, orders in the order they were first decided. */
    private static Map<String, List<Integer>> byOrder(List<Decision> decisions) {
        Map<String, List<Integer>> byOrder = new LinkedHashMap<>();
        for (int i = 0; i < decisions.size(); i++) {
            byOrder.computeIfAbsent(decisions.get(i).orderId(), id -> new ArrayList<>()).add(i);
        }
        return byOrder;
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
