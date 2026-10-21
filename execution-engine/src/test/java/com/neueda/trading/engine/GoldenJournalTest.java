package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replays every golden journal in {@code src/test/resources/journals}: a change to the engine that
 * makes any recorded decision come out differently fails the build. Replace a journal only for an
 * intended change of behaviour, explained in the pull request (see the README).
 */
class GoldenJournalTest {

    @TestFactory
    Stream<DynamicTest> everyGoldenJournalReplaysWithTheSameDecisions() throws Exception {
        List<Path> journals = goldenJournals();
        // An empty folder must not count as a pass
        assertFalse(journals.isEmpty(), "no journals found in src/test/resources/journals");
        return journals.stream().map(journal -> DynamicTest.dynamicTest(journal.getFileName().toString(), () -> {
            Replay.Result result = new Replay(
                JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build()).run(journal);
            assertTrue(result.matches(), () -> result.differences().size() + " decision(s) changed in "
                + journal.getFileName() + ":\n  " + String.join("\n  ", result.differences().stream().map(Object::toString).toList()));
        }));
    }

    private static List<Path> goldenJournals() throws Exception {
        Path dir = folder();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                .filter(f -> f.getFileName().toString().matches(".*\\.jsonl(\\.gz)?"))
                .sorted()
                .toList();
        }
    }

    private static Path folder() throws URISyntaxException {
        var url = GoldenJournalTest.class.getResource("/journals");
        if (url == null) {
            throw new IllegalStateException("src/test/resources/journals is missing");
        }
        return Path.of(url.toURI());
    }
}
