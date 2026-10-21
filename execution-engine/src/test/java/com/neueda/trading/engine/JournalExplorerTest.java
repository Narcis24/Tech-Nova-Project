package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

import com.neueda.events.OrderPlacedEvent;

class JournalExplorerTest {

    // The hand-made golden journal: 4 engine runs, 10 orders (o6 resent after a restart), 14 decisions
    private static final String EDGE_CASES = "/journals/2026-10-14-edge-cases.jsonl";
    private static final String O6 = "00000000-0000-0000-0000-000000000006";

    @TempDir
    Path dir;

    private JournalExplorer explorer;

    @BeforeEach
    void setUp() throws Exception {
        Files.copy(Path.of(getClass().getResource(EDGE_CASES).toURI()), dir.resolve("2026-10-14.jsonl"));
        explorer = new JournalExplorer(dir.toString());
    }

    @Test
    void listsTheDaysNewestFirst() throws Exception {
        Files.writeString(dir.resolve("2026-10-15.jsonl.gz"), "x");
        Files.writeString(dir.resolve("notes.txt"), "not a journal");

        assertEquals(List.of("2026-10-15", "2026-10-14"),
            explorer.days().stream().map(JournalExplorer.DayFile::day).toList());
    }

    @Test
    void summarisesADay() throws Exception {
        JournalExplorer.DaySummary day = explorer.summary("2026-10-14").orElseThrow();

        assertEquals(4, day.engineRuns());
        assertEquals(10, day.quotes());
        assertEquals(10, day.orders());                       // distinct orders, not the 11 ORDER_RECEIVED lines
        assertEquals(5, day.fills());
        assertEquals(5, day.rejects());
        assertEquals(3, day.waits());
        assertEquals(1, day.queued());
        assertEquals(new JournalExplorer.ReasonCount("No fresh quote for TSLA", 2), day.rejectReasons().getFirst());
        assertEquals("2026-10-14T13:29:00Z", day.firstTime());
    }

    @Test
    void eachOrderRowShowsWhereTheOrderEndedUp() throws Exception {
        Map<String, JournalExplorer.OrderRow> rows = explorer.summary("2026-10-14").orElseThrow().orderRows().stream()
            .collect(java.util.stream.Collectors.toMap(r -> r.orderId().substring(34), r -> r));

        JournalExplorer.OrderRow o6 = rows.get("06");
        assertEquals("FILLED", o6.outcome());
        assertEquals(0, new BigDecimal("199.50").compareTo(o6.fillPrice()));
        assertEquals(1, o6.resends());
        assertEquals(0, new BigDecimal("200.00").compareTo(o6.limitPrice()));

        JournalExplorer.OrderRow o5 = rows.get("05");
        assertEquals("REJECTED", o5.outcome());
        assertEquals("No usable ask for GLD", o5.reason());
        assertEquals(null, o5.limitPrice());                     // a market order has no limit
    }

    @Test
    void anOrderTimelineShowsTheRestartAndTheQuoteBehindEachDecision() throws Exception {
        List<JournalExplorer.TimelineEntry> timeline = explorer.timeline("2026-10-14", O6).orElseThrow();

        assertEquals(List.of("ORDER_RECEIVED", "DECISION", "ENGINE_STARTED", "ORDER_RECEIVED", "DECISION"),
            timeline.stream().map(JournalExplorer.TimelineEntry::type).toList());
        assertEquals("WAIT", timeline.get(1).decision());
        assertEquals(0, timeline.get(1).quote().path("ask").decimalValue().signum());     // GLD had no usable ask
        assertEquals("FILL", timeline.get(4).decision());
        assertEquals(0, new BigDecimal("199.50").compareTo(timeline.get(4).quote().path("ask").decimalValue()));
    }

    @Test
    void aCancelledOrderShowsAsCancelled() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-21T15:00:00Z"));
        var mapper = JsonMapper.builder().build();
        ExecutionEngine engine = new ExecutionEngine(mapper, mock(EventProducerService.class), new QuoteCache(),
            new MarketDataProperties(List.of("AAPL"), 120, 25, 2000, 600, "http://x", "", ""),
            new DecisionJournal(mapper, dir.toString(), true), clock, true);
        OrderPlacedEvent limit = new OrderPlacedEvent(UUID.randomUUID(), "ACC1", "AAPL", "BUY", "LIMIT", 5,
            new BigDecimal("90.00"), "key");
        engine.handle(limit);
        engine.cancel(limit.orderId());

        JournalExplorer.OrderRow row = explorer.summary("2026-10-21").orElseThrow().orderRows().getFirst();

        assertEquals("CANCELLED", row.outcome());
        assertEquals(List.of("ORDER_RECEIVED", "DECISION", "ORDER_CANCELLED"),
            explorer.timeline("2026-10-21", limit.orderId().toString()).orElseThrow().stream()
                .map(JournalExplorer.TimelineEntry::type).toList());
    }

    @Test
    void neverReadsAFileThatIsNotADayJournal() throws Exception {
        Files.writeString(dir.resolve("secret.jsonl"), "{}");

        assertTrue(explorer.file("../secret").isEmpty());
        assertTrue(explorer.file("secret").isEmpty());
        assertTrue(explorer.summary("2026-01-01").isEmpty());
        assertTrue(explorer.timeline("2026-10-14", "no-such-order").isEmpty());
    }
}
