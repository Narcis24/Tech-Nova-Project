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
        assertTrue(result.matches(), () -> String.join("\n", result.differences()));
    }

    @Test
    void ordersQueuedWhileClosedReplayTheSame() throws Exception {
        when(clock.instant()).thenReturn(CLOSED);
        ExecutionEngine engine = recordingEngine(true);
        engine.accept(quote("99.50", "100.50", CLOSED));
        engine.handle(order("MARKET", null));

        Replay.Result result = replay("2026-10-16");

        assertEquals("QUEUED_MARKET_CLOSED", result.recorded().getFirst().decision());
        assertTrue(result.matches(), () -> String.join("\n", result.differences()));
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
        assertTrue(result.matches(), () -> String.join("\n", result.differences()));
    }

    @Test
    void replaysWithTheSettingsTheEngineHad() throws Exception {
        when(clock.instant()).thenReturn(CLOSED);
        ExecutionEngine engine = recordingEngine(false);            // market hours off
        engine.accept(quote("99.50", "100.50", CLOSED));
        engine.handle(order("MARKET", null));                       // fills although it is night

        Replay.Result result = replay("2026-10-16");

        assertEquals("FILL", result.recorded().getFirst().decision());
        assertTrue(result.matches(), () -> String.join("\n", result.differences()));
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
}
