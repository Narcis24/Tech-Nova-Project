package com.neueda.app.controllers;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicPieController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicPieControllerTest {
    private static final UUID PIE_ID = UUID.randomUUID();
    @Autowired private MockMvc mockMvc;
    @MockitoBean private PublicPieService publicPieService;
    @MockitoBean private JwtUtil jwtUtil;

    @Test
    void listShowsPublishedTemplatesWithoutPerformanceFields() throws Exception {
        when(publicPieService.list()).thenReturn(List.of(new PublicPieSummary(PIE_ID, "Tech", "desc", "ana", 3, 5,
            LocalDateTime.of(2026, 1, 1, 0, 0), false)));

        mockMvc.perform(get("/v1/pies/public"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].pieId").value(PIE_ID.toString()))
            .andExpect(jsonPath("$[0].authorUsername").value("ana"))
            .andExpect(jsonPath("$[0].sliceCount").value(3))
            .andExpect(jsonPath("$[0].returnPct").doesNotExist())
            .andExpect(jsonPath("$[0].followers").value(5))
            .andExpect(jsonPath("$[0].demo").value(false))
            .andExpect(jsonPath("$[0].publishedAt").exists());
    }

    @Test
    void getShowsCompositionWithoutAccountOrPerformanceData() throws Exception {
        when(publicPieService.get(PIE_ID)).thenReturn(new PublicPieResponse(PIE_ID, "Tech", "desc", "ana", 1,
            5, LocalDateTime.of(2026, 1, 1, 0, 0), false,
            List.of(new PieSliceResponse("AAPL", "Apple Inc.", new BigDecimal("60"))), new BigDecimal("40")));

        mockMvc.perform(get("/v1/pies/public/" + PIE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorUsername").value("ana"))
            .andExpect(jsonPath("$.slices[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$.cashPct").value(40))
            .andExpect(jsonPath("$.points").doesNotExist())
            .andExpect(jsonPath("$.returns").doesNotExist())
            .andExpect(jsonPath("$.returnPct").doesNotExist())
            .andExpect(jsonPath("$.followers").value(5))
            .andExpect(jsonPath("$.linkedAccountIds").doesNotExist())
            .andExpect(jsonPath("$.accountId").doesNotExist());
    }

    @Test
    void getUnknownOrPrivateReturns404() throws Exception {
        when(publicPieService.get(PIE_ID)).thenThrow(new PieNotFoundException("Pie not found: " + PIE_ID));
        mockMvc.perform(get("/v1/pies/public/" + PIE_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PIE_NOT_FOUND"));
    }
}
