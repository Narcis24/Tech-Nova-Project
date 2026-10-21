package com.neueda.app.controllers;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.neueda.app.configs.SecurityConfig;
import com.neueda.app.dtos.JournalDtos.JournalDay;
import com.neueda.app.dtos.JournalDtos.JournalVerifyResult;
import com.neueda.app.exceptions.JournalException;
import com.neueda.app.services.EngineClient;
import com.neueda.app.utils.JwtUtil;

/** Runs with the real security chain (JwtFilter + SecurityConfig), like AdminAnalyticsControllerTest. */
@WebMvcTest(AdminJournalController.class)
@Import(SecurityConfig.class)
class AdminJournalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EngineClient engineClient;

    @MockitoBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void tokens() {
        when(jwtUtil.isTokenValid("admin-token")).thenReturn(true);
        when(jwtUtil.extractUsername("admin-token")).thenReturn("test_admin");
        when(jwtUtil.extractRoles("admin-token")).thenReturn(List.of("ADMIN"));

        when(jwtUtil.isTokenValid("trader-token")).thenReturn(true);
        when(jwtUtil.extractUsername("trader-token")).thenReturn("trader_one");
        when(jwtUtil.extractRoles("trader-token")).thenReturn(List.of("TRADER"));
    }

    @Test
    void onlyAdminsSeeTheJournal() throws Exception {
        mockMvc.perform(get("/v1/admin/journal/days")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/admin/journal/days").header("Authorization", "Bearer trader-token"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(engineClient);
    }

    @Test
    void anAdminGetsTheDays() throws Exception {
        when(engineClient.days()).thenReturn(List.of(new JournalDay("2026-10-20", 4_200_000)));

        mockMvc.perform(get("/v1/admin/journal/days").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].day").value("2026-10-20"));
    }

    @Test
    void anAdminCanVerifyADay() throws Exception {
        when(engineClient.verify("2026-10-20"))
            .thenReturn(new JournalVerifyResult("2026-10-20", 1, 3, 700, 4, true, List.of()));

        mockMvc.perform(post("/v1/admin/journal/days/2026-10-20/verify").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matches").value(true));
    }

    @Test
    void downloadIsAGzipFile() throws Exception {
        when(engineClient.download("2026-10-20")).thenReturn(new byte[] {31, -117, 8});

        mockMvc.perform(get("/v1/admin/journal/days/2026-10-20/download").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"2026-10-20.jsonl.gz\""))
            .andExpect(content().contentType("application/gzip"));
    }

    @Test
    void engineProblemsReachTheAdminAsClearErrors() throws Exception {
        when(engineClient.day("2026-01-01")).thenThrow(
            new JournalException(HttpStatus.NOT_FOUND, "JOURNAL_NOT_FOUND", "No journal for that day or order"));

        mockMvc.perform(get("/v1/admin/journal/days/2026-01-01").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("JOURNAL_NOT_FOUND"));
    }
}
