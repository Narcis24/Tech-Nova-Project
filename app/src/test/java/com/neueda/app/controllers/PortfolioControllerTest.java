package com.neueda.app.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.app.dtos.ErrorResponse;
import com.neueda.app.dtos.PortfolioMetricsResponse;
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.services.PortfolioService;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@RestControllerAdvice
class PortfolioGlobalExceptionHandler {
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        ErrorResponse error = new ErrorResponse("ACCOUNT_NOT_FOUND", ex.getMessage(), 404);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}

public class PortfolioControllerTest {
    private MockMvc mockMvc;

    @Mock
    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        PortfolioController controller = new PortfolioController(portfolioService);
        mockMvc = standaloneSetup(controller)
            .setControllerAdvice(new PortfolioGlobalExceptionHandler())
            .build();
    }

    @Test
    void testGetPortfolioSnapshot_Success() throws Exception {
        String accountId = "ACC123";
        
        PositionResponse position1 = new PositionResponse(
            accountId, "AAPL", 100,
            new BigDecimal("150.00"), new BigDecimal("180.00"),
            new BigDecimal("18000.00"), new BigDecimal("3000.00")
        );
        
        PositionResponse position2 = new PositionResponse(
            accountId, "GOOGL", 50,
            new BigDecimal("2500.00"), new BigDecimal("2700.00"),
            new BigDecimal("135000.00"), new BigDecimal("10000.00")
        );
        
        PortfolioSnapshotResponse snapshotResponse = new PortfolioSnapshotResponse(
            accountId,
            new BigDecimal("50000.00"),
            new BigDecimal("153000.00"),
            new BigDecimal("203000.00"),
            Arrays.asList(position1, position2)
        );

        when(portfolioService.getPortfolioSnapshot(accountId))
            .thenReturn(snapshotResponse);

        mockMvc.perform(get("/v1/accounts/{accountId}/snapshot", accountId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.cashBalance").value("50000.00"))
            .andExpect(jsonPath("$.totalMarketValue").value("153000.00"))
            .andExpect(jsonPath("$.totalPortfolioValue").value("203000.00"))
            .andExpect(jsonPath("$.positions.length()").value(2));
    }

    @Test
    void testGetPortfolioSnapshot_WithEmptyPositions() throws Exception {
        String accountId = "ACC124";
        
        PortfolioSnapshotResponse snapshotResponse = new PortfolioSnapshotResponse(
            accountId,
            new BigDecimal("50000.00"),
            new BigDecimal("0.00"),
            new BigDecimal("50000.00"),
            Collections.emptyList()
        );

        when(portfolioService.getPortfolioSnapshot(accountId))
            .thenReturn(snapshotResponse);

        mockMvc.perform(get("/v1/accounts/{accountId}/snapshot", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value("50000.00"))
            .andExpect(jsonPath("$.totalMarketValue").value("0.00"))
            .andExpect(jsonPath("$.positions.length()").value(0));
    }

    @Test
    void testGetPortfolioSnapshot_AccountNotFound() throws Exception {
        String accountId = "INVALID_ACC";

        when(portfolioService.getPortfolioSnapshot(accountId))
            .thenThrow(new AccountNotFoundException("Account not found: " + accountId));

        mockMvc.perform(get("/v1/accounts/{accountId}/snapshot", accountId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testGetPortfolioMetrics_Success() throws Exception {
        String accountId = "ACC123";
        
        PositionResponse position = new PositionResponse(
            accountId, "AAPL", 100,
            new BigDecimal("150.00"), new BigDecimal("180.00"),
            new BigDecimal("18000.00"), new BigDecimal("3000.00")
        );
        
        PortfolioMetricsResponse metricsResponse = new PortfolioMetricsResponse(
            accountId,
            new BigDecimal("50000.00"),
            new BigDecimal("18000.00"),
            new BigDecimal("68000.00"),
            new BigDecimal("3000.00"),
            new BigDecimal("4.41"),
            Arrays.asList(position)
        );

        when(portfolioService.getPortfolioMetrics(accountId))
            .thenReturn(metricsResponse);

        mockMvc.perform(get("/v1/accounts/{accountId}/metrics", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.cashBalance").value("50000.00"))
            .andExpect(jsonPath("$.totalUnrealizedPnL").value("3000.00"));
    }

    @Test
    void testGetPortfolioMetrics_WithEmptyPositions() throws Exception {
        String accountId = "ACC124";
        
        PortfolioMetricsResponse metricsResponse = new PortfolioMetricsResponse(
            accountId,
            new BigDecimal("50000.00"),
            new BigDecimal("0.00"),
            new BigDecimal("50000.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            Collections.emptyList()
        );

        when(portfolioService.getPortfolioMetrics(accountId))
            .thenReturn(metricsResponse);

        mockMvc.perform(get("/v1/accounts/{accountId}/metrics", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalUnrealizedPnL").value("0.00"));
    }

    @Test
    void testGetPortfolioMetrics_AccountNotFound() throws Exception {
        String accountId = "INVALID_ACC";

        when(portfolioService.getPortfolioMetrics(accountId))
            .thenThrow(new AccountNotFoundException("Account not found: " + accountId));

        mockMvc.perform(get("/v1/accounts/{accountId}/metrics", accountId))
            .andExpect(status().isNotFound());
    }
}