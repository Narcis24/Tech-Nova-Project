package com.neueda.trading.engine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Everything the engine receives and decides, one JSON line per event, in order, so a trading
 * day can be replayed. One file per UTC day of the engine's clock: {@code <dir>/2026-10-14.jsonl}.
 * Writing never affects trading: a failure is logged and the decision goes ahead.
 */
@Component
@Slf4j
public class DecisionJournal {

    private final ObjectMapper objectMapper;
    private final Path dir;
    private final boolean enabled;
    // Counts from 1 in each engine run; ENGINE_STARTED marks where a run begins
    private long seq;
    // The file of the day being written, kept open between lines
    private BufferedWriter writer;
    private LocalDate writerDay;

    public DecisionJournal(
            ObjectMapper objectMapper,
            @Value("${execution-engine.journal.dir:journal}") String dir,
            @Value("${execution-engine.journal.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.dir = Path.of(dir);
        this.enabled = enabled;
    }

    public synchronized void record(Instant time, String type, Map<String, Object> fields) {
        if (!enabled) {
            return;
        }
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("seq", ++seq);
        line.put("time", time.toString());
        line.put("type", type);
        line.putAll(fields);
        LocalDate day = LocalDate.ofInstant(time, ZoneOffset.UTC);
        try {
            BufferedWriter out = writerFor(day);
            out.write(objectMapper.writeValueAsString(line));
            out.newLine();
            // Each line reaches the file at once, so a crash loses nothing and tail -f shows it live
            out.flush();
        } catch (IOException | JacksonException e) {
            log.error("Could not write to the decision journal for {}: {}", day, e.getMessage());
            closeQuietly();
        }
    }

    /**
     * The open file for this day; a new day closes the old file and opens the next. Keeping it open
     * avoids opening and closing the file for every line while the engine holds its lock.
     */
    private BufferedWriter writerFor(LocalDate day) throws IOException {
        if (writer == null || !day.equals(writerDay)) {
            closeQuietly();
            Files.createDirectories(dir);
            writer = Files.newBufferedWriter(dir.resolve(day + ".jsonl"), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            writerDay = day;
        }
        return writer;
    }

    @PreDestroy
    public synchronized void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException e) {
            log.warn("Could not close the decision journal for {}: {}", writerDay, e.getMessage());
        }
        writer = null;
        writerDay = null;
    }
}
