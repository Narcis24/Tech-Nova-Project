package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.neueda.events.OrderPlacedEvent;

class ReplayTest {

    // Wednesday 11:00 and Friday 03:24 in New York
    private static final Instant OPEN = Instant.parse("2026-10-14T15:00:00Z");
    private static final Instant CLOSED = Instant.parse("2026-10-16T07:24:00Z");

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private Clock clock;

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(OPEN);
    }

    /** A real engine writing a real journal, as in production. */
    private ExecutionEngine recordingEngine(boolean enforceMarketHours) {
        MarketDataProperties props =
            new MarketDataProperties(List.of("AAPL"), 120, 25, 2000, 600, "http://x", "", "");
        ExecutionEngine engine = new ExecutionEngine(mapper, mock(EventProducerService.class), new QuoteCache(),
            props, new DecisionJournal(mapper, dir.toString(), true), clock, enforceMarketHours);
        engine.announceStarted();
        return engine;
    }

    private static OrderPlacedEvent order(String type, String limit) {
        return new OrderPlacedEvent(UUID.randomUUID(), "ACC1", "AAPL", "BUY", type, 10,
            limit == null ? null : new BigDecimal(limit), "key");
    }

    private static Quote quote(String bid, String ask, Instant asOf) {
        return new Quote("AAPL", new BigDecimal(bid), new BigDecimal(ask), asOf);
    }

    private Replay.Result replay(String day) throws Exception {
        return new Replay(mapper).run(dir.resolve(day + ".jsonl"));
    }

    @Test
    void aRecordedDayReplaysWithTheSameDecisions() throws Exception {
        ExecutionEngine engine = recordingEngine(true);
        engine.handle(order("MARKET", null));                       // no quote yet: REJECT
        engine.accept(quote("99.50", "100.50", OPEN));
        engine.handle(order("MARKET", null));                       // FILL 100.50
        engine.handle(order("LIMIT", "90.00"));                     // WAIT
        when(clock.instant()).thenReturn(OPEN.plusSeconds(120));
        engine.accept(quote("88.50", "89.00", OPEN.plusSeconds(120))); // the limit fills at 89.00

        Replay.Result result = replay("2026-10-14");

        assertEquals(1, result.runs());
        assertEquals(3, result.orders());
        assertEquals(2, result.quotes());
        assertEquals(List.of("REJECT", "FILL", "WAIT", "FILL"),
            result.recorded().stream().map(Replay.Decision::decision).toList());
        assertTrue(result.matches(), () -> result.differences().toString());
    }

    @Test
    void ordersQueuedWhileClosedReplayTheSame() throws Exception {
        when(clock.instant()).thenReturn(CLOSED);
        ExecutionEngine engine = recordingEngine(true);
        engine.accept(quote("99.50", "100.50", CLOSED));
        engine.handle(order("MARKET", null));

        Replay.Result result = replay("2026-10-16");

        assertEquals("QUEUED_MARKET_CLOSED", result.recorded().getFirst().decision());
        assertTrue(result.matches(), () -> result.differences().toString());
    }

    @Test
    void aRestartStartsTheReplayEngineFresh() throws Exception {
        ExecutionEngine before = recordingEngine(true);
        before.accept(quote("99.50", "100.50", OPEN));
        // The restarted engine has no quotes, so this market order was rejected
        ExecutionEngine after = recordingEngine(true);
        after.handle(order("MARKET", null));

        Replay.Result result = replay("2026-10-14");

        assertEquals(2, result.runs());
        assertEquals("REJECT", result.recorded().getFirst().decision());
        assertTrue(result.matches(), () -> result.differences().toString());
    }

    @Test
    void replaysWithTheSettingsTheEngineHad() throws Exception {
        when(clock.instant()).thenReturn(CLOSED);
        ExecutionEngine engine = recordingEngine(false);            // market hours off
        engine.accept(quote("99.50", "100.50", CLOSED));
        engine.handle(order("MARKET", null));                       // fills although it is night

        Replay.Result result = replay("2026-10-16");

        assertEquals("FILL", result.recorded().getFirst().decision());
        assertTrue(result.matches(), () -> result.differences().toString());
    }

    @Test
    void reportsADecisionThatChangedAndExitsWithOne() throws Exception {
        ExecutionEngine engine = recordingEngine(true);
        engine.accept(quote("99.50", "100.50", OPEN));
        engine.handle(order("MARKET", null));
        Path journal = dir.resolve("2026-10-14.jsonl");
        // As if an older engine had filled at another price
        Files.writeString(journal, Files.readString(journal).replace("\"price\":100.50", "\"price\":101.00"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = Replay.execute(new String[] {journal.toString()}, new PrintStream(out));

        assertEquals(1, exit);
        String report = out.toString();
        assertTrue(report.contains("1 difference(s)"), report);
        assertTrue(report.contains("FILL 101.00") && report.contains("FILL 100.50"), report);
    }

    @Test
    void exitsWithZeroAndSaysSoWhenEverythingMatches() throws Exception {
        recordingEngine(true).handle(order("MARKET", null));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = Replay.execute(new String[] {dir.resolve("2026-10-14.jsonl").toString()}, new PrintStream(out));

        assertEquals(0, exit);
        assertTrue(out.toString().contains("all decisions match"), out.toString());
    }

    @Test
    void anEmptyOrMissingJournalIsAnErrorNotAPass() throws Exception {
        Path empty = Files.writeString(dir.resolve("empty.jsonl"), "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertEquals(1, Replay.execute(new String[] {empty.toString()}, new PrintStream(out)));
        assertEquals(1, Replay.execute(new String[] {dir.resolve("missing.jsonl").toString()}, new PrintStream(out)));
        assertEquals(1, Replay.execute(new String[] {}, new PrintStream(out)));
        assertTrue(out.toString().contains("Could not replay"), out.toString());
    }

    @Test
    void writesAReportWithEachDifferenceItsOrderAndTheQuoteBehindIt() throws Exception {
        ExecutionEngine engine = recordingEngine(true);
        engine.accept(quote("99.50", "100.50", OPEN));
        engine.handle(order("MARKET", null));
        Path journal = dir.resolve("2026-10-14.jsonl");
        Files.writeString(journal, Files.readString(journal).replace("\"price\":100.50", "\"price\":101.00"));
        Path report = dir.resolve("shadow-gate/report.json");   // the folder does not exist yet

        int exit = Replay.execute(new String[] {"--report", report.toString(), journal.toString()},
            new PrintStream(new ByteArrayOutputStream()));

        assertEquals(1, exit);
        JsonNode content = mapper.readTree(Files.readString(report));
        assertEquals(false, content.get("matches").asBoolean());
        JsonNode entry = content.get("journals").get(0);
        assertEquals(1, entry.get("decisions").asInt());
        JsonNode difference = entry.get("differences").get(0);
        assertTrue(difference.get("recorded").asString().endsWith("FILL 101.00"));
        assertTrue(difference.get("replayed").asString().endsWith("FILL 100.50"));
        assertEquals("MARKET", difference.get("order").get("orderType").asString());
        assertEquals(0, new BigDecimal("100.50").compareTo(difference.get("lastQuote").get("ask").decimalValue()));
    }

    @Test
    void replaysEveryJournalInAFolderAndFailsIfAnyChanged() throws Exception {
        recordingEngine(true).handle(order("MARKET", null));            // 2026-10-14: rejected, unchanged
        when(clock.instant()).thenReturn(CLOSED);
        recordingEngine(true).handle(order("MARKET", null));            // 2026-10-16: queued
        Path changed = dir.resolve("2026-10-16.jsonl");
        Files.writeString(changed, Files.readString(changed).replace("QUEUED_MARKET_CLOSED", "WAIT"));
        Path report = dir.resolve("report.json");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exit = Replay.execute(new String[] {"--report", report.toString(), dir.toString()}, new PrintStream(out));

        assertEquals(1, exit);
        JsonNode journals = mapper.readTree(Files.readString(report)).get("journals");
        assertEquals(2, journals.size());
        assertEquals(0, journals.get(0).get("differences").size());
        assertEquals(1, journals.get(1).get("differences").size());
        assertTrue(out.toString().contains("all decisions match") && out.toString().contains("1 difference(s)"),
            out.toString());
    }

    @Test
    void aFolderWithoutJournalsIsAnErrorNotAPass() throws Exception {
        Path empty = Files.createDirectory(dir.resolve("nothing-here"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertEquals(1, Replay.execute(new String[] {empty.toString()}, new PrintStream(out)));
        assertTrue(out.toString().contains("no journals found"), out.toString());
    }

    /** Rewrites the journal's lines; for simulating journals that differ from what the engine decides. */
    private void editJournal(String day, java.util.function.UnaryOperator<List<String>> edit) throws Exception {
        Path journal = dir.resolve(day + ".jsonl");
        Files.write(journal, edit.apply(new java.util.ArrayList<>(Files.readAllLines(journal))));
    }

    @Test
    void decisionsOfDifferentOrdersInAnotherOrderStillMatch() throws Exception {
        ExecutionEngine engine = recordingEngine(true);
        engine.handle(order("MARKET", null));                       // REJECT (no quote)
        engine.handle(order("LIMIT", "90.00"));                     // WAIT
        // Swap the two decision lines, as a race between two orders could
        editJournal("2026-10-14", lines -> {
            int reject = indexOf(lines, "\"REJECT\"");
            int wait = indexOf(lines, "\"WAIT\"");
            String first = lines.get(reject);
            lines.set(reject, lines.get(wait));
            lines.set(wait, first);
            return lines;
        });

        Replay.Result result = replay("2026-10-14");

        assertTrue(result.matches(), () -> result.differences().toString());
    }

    @Test
    void aMissingDecisionOnlyAffectsItsOwnOrder() throws Exception {
        ExecutionEngine engine = recordingEngine(true);
        OrderPlacedEvent limit = order("LIMIT", "90.00");
        engine.handle(limit);                                       // WAIT
        for (int i = 0; i < 5; i++) {
            engine.handle(order("MARKET", null));                   // 5 x REJECT, after the WAIT
        }
        editJournal("2026-10-14", lines -> {
            lines.remove(indexOf(lines, "\"WAIT\""));
            return lines;
        });

        Replay.Result result = replay("2026-10-14");

        // Compared by position, all 5 later rejections would also have shown as different
        assertEquals(1, result.differences().size(), () -> result.differences().toString());
        Replay.Difference missing = result.differences().getFirst();
        assertEquals(null, missing.recorded());
        assertEquals(limit.orderId().toString(), missing.replayed().orderId());
        assertTrue(missing.toString().startsWith("not recorded"), missing.toString());
    }

    private static int indexOf(List<String> lines, String text) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(text)) {
                return i;
            }
        }
        throw new AssertionError("no journal line contains " + text);
    }
}
