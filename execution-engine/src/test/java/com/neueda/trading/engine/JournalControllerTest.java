package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class JournalControllerTest {

    private static final String TOKEN = "test-token";

    @TempDir
    Path dir;

    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        Files.copy(Path.of(getClass().getResource("/journals/2026-10-14-edge-cases.jsonl").toURI()),
            dir.resolve("2026-10-14.jsonl"));
        mvc = mvc(TOKEN);
    }

    private MockMvc mvc(String token) {
        return MockMvcBuilders.standaloneSetup(new JournalController(new JournalExplorer(dir.toString()), new JournalVerifier()))
            .addFilters(new InternalTokenFilter(token))
            .build();
    }

    @Test
    void refusesCallersWithoutTheToken() throws Exception {
        mvc.perform(get("/internal/journal/days")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/journal/days").header("X-Internal-Token", "guess")).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesEverythingWhenNoTokenIsConfigured() throws Exception {
        mvc("").perform(get("/internal/journal/days").header("X-Internal-Token", ""))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void listsDaysAndSummarisesOne() throws Exception {
        mvc.perform(get("/internal/journal/days").header("X-Internal-Token", TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].day").value("2026-10-14"));
        mvc.perform(get("/internal/journal/days/2026-10-14").header("X-Internal-Token", TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orders").value(10))
            .andExpect(jsonPath("$.orderRows.length()").value(10));
    }

    @Test
    void showsAnOrderTimeline() throws Exception {
        mvc.perform(get("/internal/journal/days/2026-10-14/orders/00000000-0000-0000-0000-000000000006")
                .header("X-Internal-Token", TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[4].decision").value("FILL"));
    }

    @Test
    void rejectsMalformedDaysAndAnswersNotFoundForMissingOnes() throws Exception {
        mvc.perform(get("/internal/journal/days/..%2Fsecret").header("X-Internal-Token", TOKEN))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/internal/journal/days/2026-01-01").header("X-Internal-Token", TOKEN))
            .andExpect(status().isNotFound());
        mvc.perform(get("/internal/journal/days/2026-10-14/orders/nope").header("X-Internal-Token", TOKEN))
            .andExpect(status().isNotFound());
    }

    @Test
    void downloadsTheDayGzipped() throws Exception {
        byte[] body = mvc.perform(get("/internal/journal/days/2026-10-14/download").header("X-Internal-Token", TOKEN))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"2026-10-14.jsonl.gz\""))
            .andReturn().getResponse().getContentAsByteArray();

        String unzipped = new String(new GZIPInputStream(new ByteArrayInputStream(body)).readAllBytes(),
            StandardCharsets.UTF_8);
        assertEquals(Files.readString(dir.resolve("2026-10-14.jsonl")), unzipped);
    }

    @Test
    void verifiesADayWithAReplay() throws Exception {
        mvc.perform(post("/internal/journal/days/2026-10-14/verify").header("X-Internal-Token", TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matches").value(true))
            .andExpect(jsonPath("$.decisions").value(14));
    }
}
