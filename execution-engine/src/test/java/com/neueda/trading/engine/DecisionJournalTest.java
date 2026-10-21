package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class DecisionJournalTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @TempDir
    Path dir;

    private List<JsonNode> lines(String day) throws Exception {
        return Files.readAllLines(dir.resolve(day + ".jsonl")).stream().map(mapper::readTree).toList();
    }

    @Test
    void appendsOneLineAPerEventWithSeqTimeAndType() throws Exception {
        DecisionJournal journal = new DecisionJournal(mapper, dir.toString(), true);

        journal.record(Instant.parse("2026-10-14T15:00:00Z"), "ENGINE_STARTED", Map.of());
        journal.record(Instant.parse("2026-10-14T15:00:01Z"), "DECISION", Map.of("decision", "WAIT"));

        List<JsonNode> lines = lines("2026-10-14");
        assertEquals(2, lines.size());
        assertEquals(1, lines.get(0).get("seq").asLong());
        assertEquals("2026-10-14T15:00:00Z", lines.get(0).get("time").asString());
        assertEquals("ENGINE_STARTED", lines.get(0).get("type").asString());
        assertEquals(2, lines.get(1).get("seq").asLong());
        assertEquals("WAIT", lines.get(1).get("decision").asString());
    }

    @Test
    void usesOneFilePerUtcDay() throws Exception {
        DecisionJournal journal = new DecisionJournal(mapper, dir.toString(), true);

        journal.record(Instant.parse("2026-10-14T23:59:59Z"), "ENGINE_STARTED", Map.of());
        journal.record(Instant.parse("2026-10-15T00:00:00Z"), "ENGINE_STARTED", Map.of());

        assertEquals(1, lines("2026-10-14").size());
        assertEquals(1, lines("2026-10-15").size());
    }

    @Test
    void writesNothingWhenTurnedOff() throws Exception {
        DecisionJournal journal = new DecisionJournal(mapper, dir.toString(), false);

        journal.record(Instant.parse("2026-10-14T15:00:00Z"), "ENGINE_STARTED", Map.of());

        try (var files = Files.list(dir)) {
            assertFalse(files.findAny().isPresent());
        }
    }

    @Test
    void aWriteFailureNeverReachesTheCaller() throws Exception {
        // A file where the folder should be, so nothing can be written
        Path blocked = Files.writeString(dir.resolve("not-a-folder"), "x");
        DecisionJournal journal = new DecisionJournal(mapper, blocked.toString(), true);

        assertDoesNotThrow(() -> journal.record(Instant.parse("2026-10-14T15:00:00Z"), "ENGINE_STARTED", Map.of()));
    }

    @Test
    void eachLineIsInTheFileAsSoonAsItIsRecorded() throws Exception {
        DecisionJournal journal = new DecisionJournal(mapper, dir.toString(), true);

        journal.record(Instant.parse("2026-10-14T15:00:00Z"), "ENGINE_STARTED", Map.of());

        // no close(): the line was flushed, so a crash right now would not lose it
        assertEquals(1, lines("2026-10-14").size());
    }

    @Test
    void aRestartAppendsToTodaysFileInsteadOfReplacingIt() throws Exception {
        DecisionJournal before = new DecisionJournal(mapper, dir.toString(), true);
        before.record(Instant.parse("2026-10-14T15:00:00Z"), "ENGINE_STARTED", Map.of());
        before.close();

        DecisionJournal after = new DecisionJournal(mapper, dir.toString(), true);
        after.record(Instant.parse("2026-10-14T15:05:00Z"), "ENGINE_STARTED", Map.of());

        assertEquals(2, lines("2026-10-14").size());
    }

    @Test
    void movesToTheNextDaysFileAtMidnightAndKeepsWriting() throws Exception {
        DecisionJournal journal = new DecisionJournal(mapper, dir.toString(), true);

        for (int i = 0; i < 3; i++) {
            journal.record(Instant.parse("2026-10-14T23:59:5" + i + "Z"), "DECISION", Map.of("decision", "WAIT"));
        }
        journal.record(Instant.parse("2026-10-15T00:00:01Z"), "DECISION", Map.of("decision", "WAIT"));
        journal.record(Instant.parse("2026-10-15T00:00:02Z"), "DECISION", Map.of("decision", "WAIT"));

        assertEquals(3, lines("2026-10-14").size());
        assertEquals(2, lines("2026-10-15").size());
    }
}
