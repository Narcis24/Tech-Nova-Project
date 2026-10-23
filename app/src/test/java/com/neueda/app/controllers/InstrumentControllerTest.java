package com.neueda.app.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.neueda.app.dtos.FundamentalsResponse;
import com.neueda.app.dtos.InstrumentResponse;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.services.FundamentalsService;
import com.neueda.app.services.InstrumentService;
import com.neueda.app.utils.JwtUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstrumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class InstrumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstrumentService instrumentService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private FundamentalsService fundamentalsService;

    @Test
    void fundamentalsReturnsWhatThePipelineStored() throws Exception {
        FundamentalsResponse aapl = new FundamentalsResponse("AAPL", "EQUITY", "Technology", "Consumer Electronics",
            null, null, "Apple designs phones.", "https://www.apple.com", 150000, new BigDecimal("5081985449984"),
            null, new BigDecimal("39.93"), null, new BigDecimal("8.72"), null, new BigDecimal("0.0031"), null,
            null, null, null, null, null, Instant.parse("2026-10-23T12:00:00Z"),
            List.of(new FundamentalsResponse.FinancialPeriod(LocalDate.of(2026, 6, 30), new BigDecimal("109417000000"),
                null, null, new BigDecimal("29789000000"), new BigDecimal("2.02"))),
            List.of());
        when(fundamentalsService.getFundamentals("AAPL")).thenReturn(Optional.of(aapl));

        mockMvc.perform(get("/v1/instruments/{symbol}/fundamentals", "aapl"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sector").value("Technology"))
            .andExpect(jsonPath("$.epsTtm").value(8.72))
            .andExpect(jsonPath("$.quarters[0].periodEnd").value("2026-06-30"))
            .andExpect(jsonPath("$.quarters[0].netIncome").value(29789000000L));
    }

    @Test
    void fundamentalsAreNoContentUntilLoaded() throws Exception {
        when(fundamentalsService.getFundamentals("AAPL")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/instruments/{symbol}/fundamentals", "AAPL")).andExpect(status().isNoContent());
    }

    @Test
    void testGetInstrument_Success() throws Exception {
        InstrumentResponse response = new InstrumentResponse("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
        when(instrumentService.getInstrument("AAPL")).thenReturn(response);

        mockMvc.perform(get("/v1/instruments/{symbol}", "AAPL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("AAPL"))
            .andExpect(jsonPath("$.tradable").value(true));
    }

    @Test
    void testGetInstrument_NotFound() throws Exception {
        when(instrumentService.getInstrument("FAKE"))
            .thenThrow(new InstrumentNotFoundException("Instrument not found: FAKE"));

        mockMvc.perform(get("/v1/instruments/{symbol}", "FAKE"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testGetInstruments_AllWithoutFilter() throws Exception {
        when(instrumentService.getInstruments(null)).thenReturn(List.of(
            new InstrumentResponse("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true),
            new InstrumentResponse("OLD", "Delisted Co.", AssetClass.EQUITY, "USD", false)));

        mockMvc.perform(get("/v1/instruments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetInstruments_TradableOnly() throws Exception {
        InstrumentResponse response = new InstrumentResponse("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
        when(instrumentService.getInstruments(true)).thenReturn(List.of(response));

        mockMvc.perform(get("/v1/instruments").param("tradable", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$[0].tradable").value(true));
    }

    @Test
    void testGetInstruments_NotTradableOnly() throws Exception {
        InstrumentResponse response = new InstrumentResponse("OLD", "Delisted Co.", AssetClass.EQUITY, "USD", false);
        when(instrumentService.getInstruments(false)).thenReturn(List.of(response));

        mockMvc.perform(get("/v1/instruments").param("tradable", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].tradable").value(false));
    }

    @Test
    void testGetInstruments_InvalidFilter() throws Exception {
        mockMvc.perform(get("/v1/instruments").param("tradable", "maybe"))
            .andExpect(status().isBadRequest());
    }
}
