package com.neueda.app.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.services.PositionService;
import com.neueda.app.dtos.PositionMetricsResponse;
import com.neueda.app.exceptions.TradingException;

import java.util.List;
import java.util.Collections;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.ActiveProfiles;

@WebMvcTest(PositionController.class)
@ActiveProfiles("test")
class PositionControllerTest {

    @Autowired 
    private MockMvc mockMvc;  

    @Autowired
    private ObjectMapper objectMapper;  

    @MockBean
    private PositionService positionService;  

    @Test 
    void testGetPosition_Success() throws Exception {
        
        PositionResponse mockResponse = new PositionResponse(
            "ACC123",                          // accountId
            "AAPL",                           // symbol
            100,                              // quantity
            new BigDecimal("150.00"),         // averageCost
            new BigDecimal("155.00"),         // currentPrice
            new BigDecimal("15500.00"),       // marketValue
            new BigDecimal("500.00")          // unrealizedPnL
        );
  
        
        when(positionService.getPosition("ACC123", "AAPL"))
        .thenReturn(mockResponse);
  
        
        mockMvc.perform(get("/v1/positions/ACC123/AAPL"))
        .andExpect(status().isOk());
    }
    @Test
    void testGetPosition_NotFound() throws Exception {
    
        when(positionService.getPosition("ACC123", "AAPL"))
            .thenThrow(new TradingException("Position not found"));
        
        
        mockMvc.perform(get("/v1/positions/ACC123/AAPL"))
            .andExpect(status().isBadRequest()); 
    }

    @Test
    void testGetAccountPositions_Success() throws Exception {
        List<PositionResponse> mockPositions = List.of(
            new PositionResponse("ACC123", "AAPL", 100, 
                new BigDecimal("150.00"), new BigDecimal("155.00"),
                new BigDecimal("15500.00"), new BigDecimal("500.00")),
            new PositionResponse("ACC123", "GOOGL", 50,
                new BigDecimal("2800.00"), new BigDecimal("2850.00"),
                new BigDecimal("142500.00"), new BigDecimal("2500.00"))
        );
        
        when(positionService.getAccountPositions("ACC123"))
            .thenReturn(mockPositions);
        
        mockMvc.perform(get("/v1/positions/ACC123"))
            .andExpect(status().isOk());
    }

    @Test
    void testGetAccountPositions_EmptyList() throws Exception {
        when(positionService.getAccountPositions("ACC123"))
            .thenReturn(Collections.emptyList());
        
        mockMvc.perform(get("/v1/positions/ACC123"))
            .andExpect(status().isOk());
    }

    @Test
    void testGetAccountPositions_AccountNotFound() throws Exception {
        when(positionService.getAccountPositions("INVALID"))
            .thenThrow(new TradingException("Account not found"));
        
        mockMvc.perform(get("/v1/positions/INVALID"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testGetPositionMetrics_Success() throws Exception {
        PositionMetricsResponse mockMetrics = new PositionMetricsResponse(
            "ACC123", "AAPL", 100,
            new BigDecimal("150.00"), new BigDecimal("155.00"),
            new BigDecimal("15500.00"), new BigDecimal("500.00"),
            new BigDecimal("0.0333")  // return percentage
        );
        
        when(positionService.getPositionMetrics("ACC123", "AAPL"))
            .thenReturn(mockMetrics);
        
        mockMvc.perform(get("/v1/positions/ACC123/AAPL/metrics"))
            .andExpect(status().isOk());
    }

    @Test
    void testGetPositionMetrics_ZeroCostBasis() throws Exception {
        PositionMetricsResponse mockMetrics = new PositionMetricsResponse(
            "ACC123", "AAPL", 0,
            new BigDecimal("0.00"), new BigDecimal("155.00"),
            new BigDecimal("0.00"), new BigDecimal("0.00"),
            new BigDecimal("0.00")  // return percentage is 0 when cost basis is 0
        );
        
        when(positionService.getPositionMetrics("ACC123", "AAPL"))
            .thenReturn(mockMetrics);
        
        mockMvc.perform(get("/v1/positions/ACC123/AAPL/metrics"))
            .andExpect(status().isOk());
    }

    @Test
    void testGetPositionMetrics_NotFound() throws Exception {
        when(positionService.getPositionMetrics("ACC123", "AAPL"))
            .thenThrow(new TradingException("Position not found"));
        
        mockMvc.perform(get("/v1/positions/ACC123/AAPL/metrics"))
            .andExpect(status().isBadRequest());
    }
}