package com.neueda.trading.engine;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QuoteControllerTest {

    private static final String TOKEN = "test-token";

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        QuoteCache cache = new QuoteCache();
        cache.put(new Quote("AAPL", new BigDecimal("229.10"), new BigDecimal("229.14"), Instant.parse("2026-10-22T19:58:00Z")));
        mvc = MockMvcBuilders.standaloneSetup(new QuoteController(cache))
            .addFilters(new InternalTokenFilter(TOKEN))
            .build();
    }

    @Test
    void returnsTheLatestQuoteHoweverOld() throws Exception {
        mvc.perform(get("/internal/quotes/aapl").header("X-Internal-Token", TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("AAPL"))
            .andExpect(jsonPath("$.bid").value(229.10))
            .andExpect(jsonPath("$.ask").value(229.14));
    }

    @Test
    void aSymbolWithoutAQuoteIsNotFound() throws Exception {
        mvc.perform(get("/internal/quotes/MSFT").header("X-Internal-Token", TOKEN))
            .andExpect(status().isNotFound());
    }

    @Test
    void refusesCallersWithoutTheToken() throws Exception {
        mvc.perform(get("/internal/quotes/AAPL")).andExpect(status().isUnauthorized());
    }
}
