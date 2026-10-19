package com.neueda.trading.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

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
        Path file = dir.resolve(LocalDate.ofInstant(time, ZoneOffset.UTC) + ".jsonl");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, objectMapper.writeValueAsString(line) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | JacksonException e) {
            log.error("Could not write to the decision journal {}: {}", file, e.getMessage());
        }
    }
}
