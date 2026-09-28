package com.neueda.app.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.neueda.app.dtos.InstrumentResponse;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.services.InstrumentService;
import com.neueda.app.utils.JwtUtil;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstrumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class InstrumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InstrumentService instrumentService;

    @MockBean
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
    void testGetTradableInstruments_Success() throws Exception {
        InstrumentResponse response = new InstrumentResponse("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
        when(instrumentService.getAllTradable()).thenReturn(List.of(response));

        mockMvc.perform(get("/v1/instruments/tradable"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].symbol").value("AAPL"));
    }

    @Test
    void testIsTradable_True() throws Exception {
        when(instrumentService.isTradable("AAPL")).thenReturn(true);

        mockMvc.perform(get("/v1/instruments/{symbol}/tradable", "AAPL"))
            .andExpect(status().isOk())
            .andExpect(content().string("true"));
    }

    @Test
    void testIsTradable_NotFound() throws Exception {
        when(instrumentService.isTradable("FAKE"))
            .thenThrow(new InstrumentNotFoundException("Instrument not found: FAKE"));

        mockMvc.perform(get("/v1/instruments/{symbol}/tradable", "FAKE"))
            .andExpect(status().isNotFound());
    }
}
