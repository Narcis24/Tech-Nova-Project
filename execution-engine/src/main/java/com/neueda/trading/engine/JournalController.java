package com.neueda.trading.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPOutputStream;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The decision journals for the app's admin journal page. Internal only: the port is not published and
 * InternalTokenFilter admits only the app. Read only, apart from running a verify replay.
 */
@RestController
@RequestMapping("/internal/journal")
public class JournalController {

    private static final MediaType GZIP = MediaType.parseMediaType("application/gzip");

    private final JournalExplorer explorer;
    private final JournalVerifier verifier;

    public JournalController(JournalExplorer explorer, JournalVerifier verifier) {
        this.explorer = explorer;
        this.verifier = verifier;
    }

    @GetMapping("/days")
    public List<JournalExplorer.DayFile> days() throws IOException {
        return explorer.days();
    }

    @GetMapping("/days/{day}")
    public JournalExplorer.DaySummary day(@PathVariable String day) throws IOException {
        return explorer.summary(validDay(day)).orElseThrow(() -> notFound("No journal for " + day));
    }

    @GetMapping("/days/{day}/orders/{orderId}")
    public List<JournalExplorer.TimelineEntry> order(@PathVariable String day, @PathVariable String orderId)
            throws IOException {
        return explorer.timeline(validDay(day), orderId)
            .orElseThrow(() -> notFound("Order " + orderId + " is not in the journal of " + day));
    }

    /** The day's journal, gzipped, as a file to save. */
    @GetMapping("/days/{day}/download")
    public ResponseEntity<byte[]> download(@PathVariable String day) throws IOException {
        Path file = explorer.file(validDay(day)).orElseThrow(() -> notFound("No journal for " + day));
        byte[] gzipped = file.getFileName().toString().endsWith(".gz") ? Files.readAllBytes(file) : gzip(file);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(day + ".jsonl.gz").build().toString())
            .contentType(GZIP)
            .body(gzipped);
    }

    /** Replays the day with this engine version: does it reach every recorded decision again? */
    @PostMapping("/days/{day}/verify")
    public JournalVerifier.VerifyResult verify(@PathVariable String day) throws Exception {
        Path file = explorer.file(validDay(day)).orElseThrow(() -> notFound("No journal for " + day));
        return verifier.verify(day, file);
    }

    @ExceptionHandler(JournalVerifier.BusyException.class)
    ResponseEntity<String> busy(JournalVerifier.BusyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(TimeoutException.class)
    ResponseEntity<String> tooSlow(TimeoutException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body("The replay took too long and was stopped");
    }

    private static String validDay(String day) {
        if (!JournalExplorer.DAY.matcher(day).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A day looks like 2026-10-20");
        }
        return day;
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static byte[] gzip(Path file) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream out = new GZIPOutputStream(bytes)) {
            Files.copy(file, out);
        }
        return bytes.toByteArray();
    }
}
