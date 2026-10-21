package com.neueda.trading.engine;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the decision journals for the admin journal page: which days exist, a day's summary and orders,
 * and one order's timeline with the quote behind each decision. Read only; never touches trading.
 */
@Component
public class JournalExplorer {

    /** A day name, strictly, so a request can never name another file. */
    static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    record DayFile(String day, long sizeBytes) {
    }

    record ReasonCount(String reason, int count) {
    }

    /** One order of the day and where it ended up: FILLED, REJECTED, CANCELLED, WAITING or QUEUED. */
    record OrderRow(String orderId, String receivedAt, String accountId, String symbol, String side,
            String orderType, Integer quantity, BigDecimal limitPrice, String outcome, BigDecimal fillPrice,
            String reason, int resends) {
    }

    record DaySummary(String day, int engineRuns, int quotes, int orders, int fills, int rejects, int waits,
            int queued, int cancels, List<ReasonCount> rejectReasons, String firstTime, String lastTime,
            List<OrderRow> orderRows) {
    }

    /** A journal line about one order; a decision carries the last quote for its symbol before it. */
    record TimelineEntry(long seq, String time, String type, String decision, BigDecimal price, String reason,
            JsonNode order, JsonNode quote) {
    }

    private final Path dir;
    // Prices exactly as written (100.50, not 100.5)
    private final ObjectMapper mapper = JsonMapper.builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    public JournalExplorer(@Value("${execution-engine.journal.dir:journal}") String dir) {
        this.dir = Path.of(dir);
    }

    /** Every day that has a journal, newest first. */
    List<DayFile> days() throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                .filter(f -> f.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}\\.jsonl(\\.gz)?"))
                .map(f -> new DayFile(f.getFileName().toString().substring(0, 10), size(f)))
                .sorted(Comparator.comparing(DayFile::day).reversed())
                .toList();
        }
    }

    /** The journal file of a day, plain or gzipped; empty when the day is malformed or has no journal. */
    Optional<Path> file(String day) {
        if (day == null || !DAY.matcher(day).matches()) {
            return Optional.empty();
        }
        Path plain = dir.resolve(day + ".jsonl");
        if (Files.isRegularFile(plain)) {
            return Optional.of(plain);
        }
        Path gzipped = dir.resolve(day + ".jsonl.gz");
        return Files.isRegularFile(gzipped) ? Optional.of(gzipped) : Optional.empty();
    }

    Optional<DaySummary> summary(String day) throws IOException {
        Optional<Path> file = file(day);
        if (file.isEmpty()) {
            return Optional.empty();
        }
        int runs = 0;
        int quotes = 0;
        int fills = 0;
        int rejects = 0;
        int waits = 0;
        int queued = 0;
        int cancels = 0;
        String first = null;
        String last = null;
        Map<String, Integer> reasons = new TreeMap<>();
        Map<String, OrderState> orders = new LinkedHashMap<>();
        for (JsonNode line : lines(file.get())) {
            String time = line.path("time").asString();
            first = first == null ? time : first;
            last = time;
            switch (line.path("type").asString()) {
                case JournalTypes.ENGINE_STARTED -> runs++;
                case JournalTypes.QUOTE_RECEIVED -> quotes++;
                case JournalTypes.ORDER_RECEIVED -> {
                    JsonNode order = line.path("order");
                    OrderState state = orders.get(order.path("orderId").asString());
                    if (state == null) {
                        orders.put(order.path("orderId").asString(), new OrderState(time, order));
                    } else {
                        state.resends++;  // sent again after an engine restart
                    }
                }
                case JournalTypes.ORDER_CANCELLED -> {
                    cancels++;
                    OrderState state = orders.get(line.path("orderId").asString());
                    if (state != null && !state.closed()) {
                        state.outcome = "CANCELLED";
                    }
                }
                case JournalTypes.DECISION -> {
                    String decision = line.path("decision").asString();
                    OrderState state = orders.get(line.path("orderId").asString());
                    switch (decision) {
                        case JournalTypes.FILL -> fills++;
                        case JournalTypes.REJECT -> {
                            rejects++;
                            reasons.merge(line.path("reason").asString(), 1, Integer::sum);
                        }
                        case JournalTypes.WAIT -> waits++;
                        case JournalTypes.QUEUED_MARKET_CLOSED -> queued++;
                        default -> { }
                    }
                    if (state != null) {
                        state.decide(decision, line);
                    }
                }
                default -> { }
            }
        }
        List<ReasonCount> reasonCounts = reasons.entrySet().stream()
            .map(e -> new ReasonCount(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingInt(ReasonCount::count).reversed())
            .toList();
        List<OrderRow> rows = orders.entrySet().stream().map(e -> e.getValue().row(e.getKey())).toList();
        return Optional.of(new DaySummary(day, runs, quotes, orders.size(), fills, rejects, waits, queued, cancels,
            reasonCounts, first, last, rows));
    }

    /** Every line about one order, in journal order; empty when the day or the order is not in a journal. */
    Optional<List<TimelineEntry>> timeline(String day, String orderId) throws IOException {
        Optional<Path> file = file(day);
        if (file.isEmpty()) {
            return Optional.empty();
        }
        Map<String, JsonNode> lastQuotes = new HashMap<>();
        String symbol = null;
        boolean open = false;
        List<TimelineEntry> entries = new ArrayList<>();
        for (JsonNode line : lines(file.get())) {
            String type = line.path("type").asString();
            long seq = line.path("seq").asLong();
            String time = line.path("time").asString();
            switch (type) {
                case JournalTypes.QUOTE_RECEIVED ->
                    lastQuotes.put(line.path("quote").path("symbol").asString(), line.path("quote"));
                case JournalTypes.ENGINE_STARTED -> {
                    // A restart while the order was still open explains why it shows up again
                    if (open) {
                        entries.add(new TimelineEntry(seq, time, type, null, null, null, null, null));
                    }
                }
                case JournalTypes.ORDER_RECEIVED -> {
                    JsonNode order = line.path("order");
                    if (orderId.equals(order.path("orderId").asString())) {
                        symbol = order.path("symbol").asString();
                        open = true;
                        entries.add(new TimelineEntry(seq, time, type, null, null, null, order, null));
                    }
                }
                case JournalTypes.ORDER_CANCELLED -> {
                    if (orderId.equals(line.path("orderId").asString())) {
                        open = false;
                        entries.add(new TimelineEntry(seq, time, type, null, null, null, null, null));
                    }
                }
                case JournalTypes.DECISION -> {
                    if (orderId.equals(line.path("orderId").asString())) {
                        String decision = line.path("decision").asString();
                        open = !JournalTypes.FILL.equals(decision) && !JournalTypes.REJECT.equals(decision);
                        entries.add(new TimelineEntry(seq, time, type, decision,
                            line.has("price") ? line.path("price").decimalValue() : null,
                            line.has("reason") ? line.path("reason").asString() : null,
                            null, symbol != null ? lastQuotes.get(symbol) : null));
                    }
                }
                default -> { }
            }
        }
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries);
    }

    private List<JsonNode> lines(Path file) throws IOException {
        List<JsonNode> lines = new ArrayList<>();
        for (String text : Replay.readLines(file)) {
            if (!text.isBlank()) {
                lines.add(mapper.readTree(text));
            }
        }
        return lines;
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    /** Where one order stands while the day is read. */
    private static final class OrderState {

        private final String receivedAt;
        private final JsonNode order;
        private String outcome = "WAITING";
        private BigDecimal fillPrice;
        private String reason;
        private int resends;

        OrderState(String receivedAt, JsonNode order) {
            this.receivedAt = receivedAt;
            this.order = order;
        }

        boolean closed() {
            return "FILLED".equals(outcome) || "REJECTED".equals(outcome);
        }

        void decide(String decision, JsonNode line) {
            if (closed() || "CANCELLED".equals(outcome)) {
                return;
            }
            switch (decision) {
                case JournalTypes.FILL -> {
                    outcome = "FILLED";
                    fillPrice = line.path("price").decimalValue();
                }
                case JournalTypes.REJECT -> {
                    outcome = "REJECTED";
                    reason = line.path("reason").asString();
                }
                case JournalTypes.QUEUED_MARKET_CLOSED -> outcome = "QUEUED";
                default -> outcome = "WAITING";
            }
        }

        OrderRow row(String orderId) {
            return new OrderRow(orderId, receivedAt, order.path("accountId").asString(),
                order.path("symbol").asString(), order.path("side").asString(), order.path("orderType").asString(),
                order.path("quantity").isNumber() ? order.path("quantity").asInt() : null,
                // a market order's price is only the reference price at placement, not a limit
                "LIMIT".equals(order.path("orderType").asString()) && order.path("price").isNumber()
                    ? order.path("price").decimalValue() : null,
                outcome, fillPrice, reason, resends);
        }
    }
}
