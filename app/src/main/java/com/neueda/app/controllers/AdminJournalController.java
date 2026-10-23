package com.neueda.app.controllers;

import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.neueda.app.dtos.JournalDtos.JournalDay;
import com.neueda.app.dtos.JournalDtos.JournalDaySummary;
import com.neueda.app.dtos.JournalDtos.JournalTimelineEntry;
import com.neueda.app.dtos.JournalDtos.JournalVerifyResult;
import com.neueda.app.services.EngineClient;

/**
 * The execution engine's decision journal for the admin journal page. SecurityConfig restricts every
 * /v1/admin/** route to the ADMIN role; the engine itself is only reachable from the app.
 */
@RestController
@RequestMapping("/v1/admin/journal")
public class AdminJournalController {

    private final EngineClient engineClient;

    public AdminJournalController(EngineClient engineClient) {
        this.engineClient = engineClient;
    }

    /** Days that have a journal, newest first. */
    @GetMapping("/days")
    public List<JournalDay> days() {
        return engineClient.days();
    }

    /** A day's totals, reject reasons and every order with its outcome. */
    @GetMapping("/days/{day}")
    public JournalDaySummary day(@PathVariable String day) {
        return engineClient.day(day);
    }

    /** Every journal line about one order, each decision with the quote behind it. */
    @GetMapping("/days/{day}/orders/{orderId}")
    public List<JournalTimelineEntry> timeline(@PathVariable String day, @PathVariable String orderId) {
        return engineClient.timeline(day, orderId);
    }

    /** The day's raw journal as a gzipped file (a Resource, so the API spec and UI client treat it as binary). */
    @GetMapping(value = "/days/{day}/download", produces = "application/gzip")
    public ResponseEntity<Resource> download(@PathVariable String day) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(day + ".jsonl.gz").build().toString())
            .body(new ByteArrayResource(engineClient.download(day)));
    }

    /** Replays the day with the running engine and reports any decision that came out differently. */
    @PostMapping("/days/{day}/verify")
    public JournalVerifyResult verify(@PathVariable String day) {
        return engineClient.verify(day);
    }
}
