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

    // Field names in a journal line
    private static final String ORDER_ID = "orderId";
    private static final String PRICE = "price";
    private static final String REASON = "reason";
    private static final String SYMBOL = "symbol";

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
        DayTally tally = new DayTally();
        for (JsonNode line : lines(file.get())) {
            tally.add(line);
        }
        return Optional.of(tally.summary(day));
    }

    /** Every line about one order, in journal order; empty when the day or the order is not in a journal. */
    Optional<List<TimelineEntry>> timeline(String day, String orderId) throws IOException {
        Optional<Path> file = file(day);
        if (file.isEmpty()) {
            return Optional.empty();
        }
        Timeline timeline = new Timeline(orderId);
        for (JsonNode line : lines(file.get())) {
            timeline.add(line);
        }
        return timeline.entries.isEmpty() ? Optional.empty() : Optional.of(timeline.entries);
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

    /** The day's totals and every order's outcome, built line by line. */
    private static final class DayTally {

        private int runs;
        private int quotes;
        private int fills;
        private int rejects;
        private int waits;
        private int queued;
        private int cancels;
        private String first;
        private String last;
        private final Map<String, Integer> reasons = new TreeMap<>();
        private final Map<String, OrderState> orders = new LinkedHashMap<>();

        void add(JsonNode line) {
            String time = line.path("time").asString();
            first = first == null ? time : first;
            last = time;
            switch (line.path("type").asString()) {
                case JournalTypes.ENGINE_STARTED -> runs++;
                case JournalTypes.QUOTE_RECEIVED -> quotes++;
                case JournalTypes.ORDER_RECEIVED -> received(time, line.path("order"));
                case JournalTypes.ORDER_CANCELLED -> cancelled(line);
                case JournalTypes.DECISION -> decided(line);
                default -> {
                    // a line type this page does not count
                }
            }
        }

        private void received(String time, JsonNode order) {
            OrderState state = orders.get(order.path(ORDER_ID).asString());
            if (state == null) {
                orders.put(order.path(ORDER_ID).asString(), new OrderState(time, order));
            } else {
                state.resends++;  // sent again after an engine restart
            }
        }

        private void cancelled(JsonNode line) {
            cancels++;
            OrderState state = orders.get(line.path(ORDER_ID).asString());
            if (state != null && !state.closed()) {
                state.outcome = "CANCELLED";
            }
        }

        private void decided(JsonNode line) {
            String decision = line.path("decision").asString();
            switch (decision) {
                case JournalTypes.FILL -> fills++;
                case JournalTypes.REJECT -> {
                    rejects++;
                    reasons.merge(line.path(REASON).asString(), 1, Integer::sum);
                }
                case JournalTypes.WAIT -> waits++;
                case JournalTypes.QUEUED_MARKET_CLOSED -> queued++;
                default -> {
                    // a decision this page does not count
                }
            }
            OrderState state = orders.get(line.path(ORDER_ID).asString());
            if (state != null) {
                state.decide(decision, line);
            }
        }

        DaySummary summary(String day) {
            List<ReasonCount> reasonCounts = reasons.entrySet().stream()
                .map(e -> new ReasonCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(ReasonCount::count).reversed())
                .toList();
            List<OrderRow> rows = orders.entrySet().stream().map(e -> e.getValue().row(e.getKey())).toList();
            return new DaySummary(day, runs, quotes, orders.size(), fills, rejects, waits, queued, cancels,
                reasonCounts, first, last, rows);
        }
    }

    /** One order's lines, built line by line, with the last quote for its symbol at each decision. */
    private static final class Timeline {

        private final String orderId;
        private final Map<String, JsonNode> lastQuotes = new HashMap<>();
        private final List<TimelineEntry> entries = new ArrayList<>();
        private String symbol;
        private boolean open;

        Timeline(String orderId) {
            this.orderId = orderId;
        }

        void add(JsonNode line) {
            String type = line.path("type").asString();
            long seq = line.path("seq").asLong();
            String time = line.path("time").asString();
            switch (type) {
                case JournalTypes.QUOTE_RECEIVED ->
                    lastQuotes.put(line.path("quote").path(SYMBOL).asString(), line.path("quote"));
                // A restart while the order was still open explains why it shows up again
                case JournalTypes.ENGINE_STARTED -> addIf(open, seq, time, type);
                case JournalTypes.ORDER_RECEIVED -> received(seq, time, line.path("order"));
                case JournalTypes.ORDER_CANCELLED -> {
                    boolean ours = isOurs(line);
                    open = open && !ours;
                    addIf(ours, seq, time, type);
                }
                case JournalTypes.DECISION -> decided(seq, time, line);
                default -> {
                    // not about any order
                }
            }
        }

        private boolean isOurs(JsonNode line) {
            return orderId.equals(line.path(ORDER_ID).asString());
        }

        private void addIf(boolean add, long seq, String time, String type) {
            if (add) {
                entries.add(new TimelineEntry(seq, time, type, null, null, null, null, null));
            }
        }

        private void received(long seq, String time, JsonNode order) {
            if (!isOurs(order)) {
                return;
            }
            symbol = order.path(SYMBOL).asString();
            open = true;
            entries.add(new TimelineEntry(seq, time, JournalTypes.ORDER_RECEIVED, null, null, null, order, null));
        }

        private void decided(long seq, String time, JsonNode line) {
            if (!isOurs(line)) {
                return;
            }
            String decision = line.path("decision").asString();
            open = !JournalTypes.FILL.equals(decision) && !JournalTypes.REJECT.equals(decision);
            entries.add(new TimelineEntry(seq, time, JournalTypes.DECISION, decision,
                line.has(PRICE) ? line.path(PRICE).decimalValue() : null,
                line.has(REASON) ? line.path(REASON).asString() : null,
                null, symbol != null ? lastQuotes.get(symbol) : null));
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
                    fillPrice = line.path(PRICE).decimalValue();
                }
                case JournalTypes.REJECT -> {
                    outcome = "REJECTED";
                    reason = line.path(REASON).asString();
                }
                case JournalTypes.QUEUED_MARKET_CLOSED -> outcome = "QUEUED";
                default -> outcome = "WAITING";
            }
        }

        OrderRow row(String orderId) {
            return new OrderRow(orderId, receivedAt, order.path("accountId").asString(),
                order.path(SYMBOL).asString(), order.path("side").asString(), order.path("orderType").asString(),
                order.path("quantity").isNumber() ? order.path("quantity").asInt() : null,
                // a market order's price is only the reference price at placement, not a limit
                "LIMIT".equals(order.path("orderType").asString()) && order.path(PRICE).isNumber()
                    ? order.path(PRICE).decimalValue() : null,
                outcome, fillPrice, reason, resends);
        }
    }
}
