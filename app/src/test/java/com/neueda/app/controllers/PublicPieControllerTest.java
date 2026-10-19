package com.neueda.app.controllers;

import com.neueda.app.dtos.IndexPoint;
import com.neueda.app.dtos.PieSliceResponse;
import com.neueda.app.dtos.PublicPieResponse;
import com.neueda.app.dtos.PublicPieSummary;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.services.PublicPieService;
import com.neueda.app.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicPieController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicPieControllerTest {

    private static final UUID PIE_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicPieService publicPieService;

    @MockitoBean
    private JwtUtil jwtUtil;  // Required by JwtFilter, which is picked up in the web slice

    private PublicPieSummary summary() {
        return new PublicPieSummary(PIE_ID, "Tech", "desc", "ana", 3, new BigDecimal("12.34"), 5,
            LocalDateTime.of(2026, 1, 1, 0, 0), false);
    }

    @Test
    void list_passesWindow_andReturnsRows() throws Exception {
        when(publicPieService.list("30D")).thenReturn(List.of(summary()));

        mockMvc.perform(get("/v1/pies/public").param("window", "30D"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].pieId").value(PIE_ID.toString()))
            .andExpect(jsonPath("$[0].authorUsername").value("ana"))
            .andExpect(jsonPath("$[0].sliceCount").value(3))
            .andExpect(jsonPath("$[0].returnPct").value(12.34))
            .andExpect(jsonPath("$[0].followers").value(5))
            .andExpect(jsonPath("$[0].demo").value(false))
            .andExpect(jsonPath("$[0].publishedAt").exists());
    }

    @Test
    void list_withoutWindow_meansAll() throws Exception {
        when(publicPieService.list("ALL")).thenReturn(List.of());

        mockMvc.perform(get("/v1/pies/public")).andExpect(status().isOk());

        verify(publicPieService).list("ALL");
    }

    @Test
    void list_badWindow_400() throws Exception {
        when(publicPieService.list("1Y")).thenThrow(new IllegalArgumentException("window must be one of 7D, 30D, 90D, ALL"));

        mockMvc.perform(get("/v1/pies/public").param("window", "1Y"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));
    }

    @Test
    void get_showsPieWithoutAnyAccountOrMoney() throws Exception {
        Map<String, BigDecimal> returns = new LinkedHashMap<>();
        returns.put("7D", new BigDecimal("1.50"));
        returns.put("90D", null);
        when(publicPieService.get(PIE_ID)).thenReturn(new PublicPieResponse(PIE_ID, "Tech", "desc", "ana", 1,
            new BigDecimal("2.00"), 5, LocalDateTime.of(2026, 1, 1, 0, 0), false,
            List.of(new PieSliceResponse("AAPL", "Apple Inc.", new BigDecimal("60"))), new BigDecimal("40"),
            List.of(new IndexPoint(LocalDate.of(2026, 1, 1), new BigDecimal("100.000000"))), returns));

        mockMvc.perform(get("/v1/pies/public/" + PIE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorUsername").value("ana"))
            .andExpect(jsonPath("$.slices[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$.cashPct").value(40))
            .andExpect(jsonPath("$.points[0].date").value("2026-01-01"))
            .andExpect(jsonPath("$.points[0].value").value(100.0))
            .andExpect(jsonPath("$.returns.7D").value(1.5))
            .andExpect(jsonPath("$.returns.90D").doesNotExist())
            .andExpect(jsonPath("$.followers").value(5))
            .andExpect(jsonPath("$.linkedAccountIds").doesNotExist())
            .andExpect(jsonPath("$.accountId").doesNotExist());
    }

    @Test
    void get_unknownOrPrivate_404() throws Exception {
        when(publicPieService.get(PIE_ID)).thenThrow(new PieNotFoundException("Pie not found: " + PIE_ID));

        mockMvc.perform(get("/v1/pies/public/" + PIE_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PIE_NOT_FOUND"));
    }
}
