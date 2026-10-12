package com.neueda.app.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.neueda.app.dtos.InstrumentResponse;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.services.InstrumentService;
import com.neueda.app.utils.JwtUtil;

import java.util.List;

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
