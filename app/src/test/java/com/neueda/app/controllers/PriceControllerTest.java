package com.neueda.app.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.services.PriceService;
import com.neueda.app.utils.JwtUtil;

import java.math.BigDecimal;

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
