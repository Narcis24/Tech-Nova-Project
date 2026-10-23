package com.neueda.app.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.dtos.QuoteResponse;
import com.neueda.app.services.EngineClient;
import com.neueda.app.services.PriceService;
import com.neueda.app.utils.JwtUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PriceController.class)
@AutoConfigureMockMvc(addFilters = false)
class PriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PriceService priceService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private EngineClient engineClient;

    @Test
    void quoteReturnsTheEnginesLatestBidAndAsk() throws Exception {
        when(engineClient.quote("AAPL")).thenReturn(Optional.of(new QuoteResponse(
            "AAPL", new BigDecimal("229.10"), new BigDecimal("229.14"), Instant.parse("2026-10-22T19:58:00Z"))));

        mockMvc.perform(get("/v1/prices/{symbol}/quote", "aapl"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bid").value(229.10))
            .andExpect(jsonPath("$.ask").value(229.14))
            .andExpect(jsonPath("$.asOf").value("2026-10-22T19:58:00Z"));
    }

    @Test
    void quoteIsNoContentWhenTheEngineHasNone() throws Exception {
        when(engineClient.quote("AAPL")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/prices/{symbol}/quote", "AAPL")).andExpect(status().isNoContent());
    }

    @Test
    void testGetPrice_Success() throws Exception {
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("150.00"));

        mockMvc.perform(get("/v1/prices/{symbol}", "AAPL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("AAPL"))
            .andExpect(jsonPath("$.price").value(150.00));
    }

    @Test
    void testGetPrice_NotFound() throws Exception {
        when(priceService.getCurrentPrice("FAKE"))
            .thenThrow(new PriceNotFoundException("No price data for symbol: FAKE"));

        mockMvc.perform(get("/v1/prices/{symbol}", "FAKE"))
            .andExpect(status().isNotFound());
    }
}
