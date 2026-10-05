package com.neueda.app.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.app.dtos.PortfolioMetricsResponse;
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.services.PortfolioService;
import com.neueda.app.utils.JwtUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
@AutoConfigureMockMvc(addFilters = false)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PortfolioService portfolioService;

    @MockBean
    private JwtUtil jwtUtil;

    // ======================== GET Portfolio Snapshot Tests ========================

    @Test
    void testGetPortfolioSnapshot_Success() throws Exception {
        // STEP 1: Setup - Create test data
        String accountId = "ACC123";
        
        PositionResponse position1 = new PositionResponse(
            accountId,
            "AAPL",
            100,
            new BigDecimal("150.00"),
            new BigDecimal("180.00"),
            new BigDecimal("18000.00"),
            new BigDecimal("3000.00")
        );
        
        PositionResponse position2 = new PositionResponse(
            accountId,
            "GOOGL",
            50,
            new BigDecimal("2500.00"),
            new BigDecimal("2700.00"),
            new BigDecimal("135000.00"),
            new BigDecimal("10000.00")
        );
        
        List<PositionResponse> positions = Arrays.asList(position1, position2);
        
        PortfolioSnapshotResponse snapshotResponse = new PortfolioSnapshotResponse(
            accountId,
            new BigDecimal("50000.00"),  // cashBalance
            new BigDecimal("153000.00"), // totalMarketValue (18000 + 135000)
            new BigDecimal("203000.00"), // totalPortfolioValue (50000 + 153000)
            positions
        );

        // STEP 2: Setup - Mock the service
        when(portfolioService.getPortfolioSnapshot(accountId))
            .thenReturn(snapshotResponse);

        // STEP 3: Act & Assert - Make the HTTP GET request and verify response
        mockMvc.perform(get("/v1/accounts/{accountId}/snapshot", accountId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.cashBalance").value("50000.00"))
            .andExpect(jsonPath("$.totalMarketValue").value("153000.00"))
            .andExpect(jsonPath("$.totalPortfolioValue").value("203000.00"))
            .andExpect(jsonPath("$.positions.length()").value(2))
            .andExpect(jsonPath("$.positions[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$.positions[0].quantity").value(100))
            .andExpect(jsonPath("$.positions[0].currentPrice").value("180.00"))
            .andExpect(jsonPath("$.positions[0].marketValue").value("18000.00"))
            .andExpect(jsonPath("$.positions[0].unrealizedPnL").value("3000.00"))
            .andExpect(jsonPath("$.positions[1].symbol").value("GOOGL"))
            .andExpect(jsonPath("$.positions[1].quantity").value(50))
            .andExpect(jsonPath("$.positions[1].currentPrice").value("2700.00"))
            .andExpect(jsonPath("$.positions[1].marketValue").value("135000.00"))
            .andExpect(jsonPath("$.positions[1].unrealizedPnL").value("10000.00"));
    }

    @Test
    void testGetPortfolioSnapshot_WithSinglePosition() throws Exception {
        // STEP 1: Setup - Create test data with single position
        String accountId = "ACC456";
        
        PositionResponse position = new PositionResponse(
            accountId,
            "MSFT",
            200,
            new BigDecimal("300.00"),
            new BigDecimal("350.00"),
            new BigDecimal("70000.00"),
            new BigDecimal("10000.00")
        );
        
        PortfolioSnapshotResponse snapshotResponse = new PortfolioSnapshotResponse(
            accountId,
            new BigDecimal("100000.00"),
            new BigDecimal("70000.00"),
            new BigDecimal("170000.00"),
            Arrays.asList(position)
        );

        // STEP 2: Setup - Mock the service
        when(portfolioService.getPortfolioSnapshot(accountId))
            .thenReturn(snapshotResponse);

        // STEP 3: Act & Assert
        mockMvc.perform(get("/v1/accounts/{accountId}/snapshot", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.cashBalance").value("100000.00"))
            .andExpect(jsonPath("$.totalMarketValue").value("70000.00"))
            .andExpect(jsonPath("$.totalPortfolioValue").value("170000.00"))
            .andExpect(jsonPath("$.positions.length()").value(1))
            .andExpect(jsonPath("$.positions[0].symbol").value("MSFT"));
    }

    @Test
    void testGetPortfolioSnapshot_WithEmptyPositions() throws Exception {
        // STEP 1: Setup - Create test data with no positions
        String accountId = "ACC789";
        
        PortfolioSnapshotResponse snapshotResponse = new PortfolioSnapshotResponse(
            accountId,
            new BigDecimal("50000.00"),
            new BigDecimal("0.00"),
            new BigDecimal("50000.00"),
            Arrays.asList()
        );

        // STEP 2: Setup - Mock the service
        when(portfolioService.getPortfolioSnapshot(accountId))
            .thenReturn(snapshotResponse);

        // STEP 3: Act & Assert
        mockMvc.perform(get("/v1/accounts/{accountId}/snapshot", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.cashBalance").value("50000.00"))
            .andExpect(jsonPath("$.totalMarketValue").value("0.00"))
            .andExpect(jsonPath("$.totalPortfolioValue").value("50000.00"))
            .andExpect(jsonPath("$.positions.length()").value(0));
    }

    @Test
    void testGetPortfolioSnapshot_AccountNotFound() throws Exception {
        // STEP 1: Setup - Create test data
        String accountId = "INVALID_ACC";

        // STEP 2: Setup - Mock the service to throw exception
        when(portfolioService.getPortfolioSnapshot(accountId))
            .thenThrow(new AccountNotFoundException("Account not found: " + accountId));

        // STEP 3: Act & Assert - Verify 404 Not Found
        mockMvc.perform(get("/v1/accounts/{accountId}/snapshot", accountId))
            .andExpect(status().isNotFound());
    }

    // ======================== GET Portfolio Metrics Tests ========================

    @Test
    void testGetPortfolioMetrics_Success() throws Exception {
        // STEP 1: Setup - Create test data
        String accountId = "ACC123";
        
        PositionResponse position1 = new PositionResponse(
            accountId,
            "AAPL",
            100,
            new BigDecimal("150.00"),
            new BigDecimal("180.00"),
            new BigDecimal("18000.00"),
            new BigDecimal("3000.00")
        );
        
        PositionResponse position2 = new PositionResponse(
            accountId,
            "GOOGL",
            50,
            new BigDecimal("2500.00"),
            new BigDecimal("2700.00"),
            new BigDecimal("135000.00"),
            new BigDecimal("10000.00")
        );
        
        List<PositionResponse> positions = Arrays.asList(position1, position2);
        
        PortfolioMetricsResponse metricsResponse = new PortfolioMetricsResponse(
            accountId,
            new BigDecimal("50000.00"),  // cashBalance
            new BigDecimal("153000.00"), // totalMarketValue
            new BigDecimal("203000.00"), // totalPortfolioValue
            new BigDecimal("13000.00"),  // totalUnrealizedPnL (3000 + 10000)
            new BigDecimal("6.40"),      // portfolioReturn (13000/203000 * 100 ≈ 6.40%)
            positions
        );

        // STEP 2: Setup - Mock the service
        when(portfolioService.getPortfolioMetrics(accountId))
            .thenReturn(metricsResponse);

        // STEP 3: Act & Assert - Make the HTTP GET request and verify response
        mockMvc.perform(get("/v1/accounts/{accountId}/metrics", accountId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.cashBalance").value("50000.00"))
            .andExpect(jsonPath("$.totalMarketValue").value("153000.00"))
            .andExpect(jsonPath("$.totalPortfolioValue").value("203000.00"))
            .andExpect(jsonPath("$.totalUnrealizedPnL").value("13000.00"))
            .andExpect(jsonPath("$.portfolioReturn").value("6.40"))
            .andExpect(jsonPath("$.positions.length()").value(2))
            .andExpect(jsonPath("$.positions[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$.positions[0].unrealizedPnL").value("3000.00"))
            .andExpect(jsonPath("$.positions[1].symbol").value("GOOGL"))
            .andExpect(jsonPath("$.positions[1].unrealizedPnL").value("10000.00"));
    }

    @Test
    void testGetPortfolioMetrics_WithNegativeReturns() throws Exception {
        // STEP 1: Setup - Create test data with negative returns
        String accountId = "ACC999";
        
        PositionResponse position1 = new PositionResponse(
            accountId,
            "AAPL",
            100,
            new BigDecimal("200.00"),
            new BigDecimal("150.00"),  // Price decreased
            new BigDecimal("15000.00"),
            new BigDecimal("-5000.00")  // Loss
        );
        
        PositionResponse position2 = new PositionResponse(
            accountId,
            "TSLA",
            50,
            new BigDecimal("300.00"),
            new BigDecimal("250.00"),  // Price decreased
            new BigDecimal("12500.00"),
            new BigDecimal("-2500.00")  // Loss
        );
        
        List<PositionResponse> positions = Arrays.asList(position1, position2);
        
        PortfolioMetricsResponse metricsResponse = new PortfolioMetricsResponse(
            accountId,
            new BigDecimal("100000.00"),
            new BigDecimal("27500.00"),  // totalMarketValue
            new BigDecimal("127500.00"), // totalPortfolioValue
            new BigDecimal("-7500.00"),  // totalUnrealizedPnL (negative)
            new BigDecimal("-5.88"),     // portfolioReturn (negative)
            positions
        );

        // STEP 2: Setup - Mock the service
        when(portfolioService.getPortfolioMetrics(accountId))
            .thenReturn(metricsResponse);

        // STEP 3: Act & Assert
        mockMvc.perform(get("/v1/accounts/{accountId}/metrics", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.totalUnrealizedPnL").value("-7500.00"))
            .andExpect(jsonPath("$.portfolioReturn").value("-5.88"))
            .andExpect(jsonPath("$.positions[0].unrealizedPnL").value("-5000.00"))
            .andExpect(jsonPath("$.positions[1].unrealizedPnL").value("-2500.00"));
    }

    @Test
    void testGetPortfolioMetrics_WithBreakEvenPositions() throws Exception {
        // STEP 1: Setup - Create test data with break-even positions
        String accountId = "ACC555";
        
        PositionResponse position = new PositionResponse(
            accountId,
            "NFLX",
            100,
            new BigDecimal("250.00"),
            new BigDecimal("250.00"),  // Price unchanged
            new BigDecimal("25000.00"),
            new BigDecimal("0.00")     // No gain/loss
        );
        
        PortfolioMetricsResponse metricsResponse = new PortfolioMetricsResponse(
            accountId,
            new BigDecimal("75000.00"),
            new BigDecimal("25000.00"),
            new BigDecimal("100000.00"),
            new BigDecimal("0.00"),    // No unrealized PnL
            new BigDecimal("0.00"),    // No return
            Arrays.asList(position)
        );

        // STEP 2: Setup - Mock the service
        when(portfolioService.getPortfolioMetrics(accountId))
            .thenReturn(metricsResponse);

        // STEP 3: Act & Assert
        mockMvc.perform(get("/v1/accounts/{accountId}/metrics", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalUnrealizedPnL").value("0.00"))
            .andExpect(jsonPath("$.portfolioReturn").value("0.00"))
            .andExpect(jsonPath("$.positions[0].unrealizedPnL").value("0.00"));
    }

    @Test
    void testGetPortfolioMetrics_WithEmptyPositions() throws Exception {
        // STEP 1: Setup - Create test data with no positions
        String accountId = "ACC111";
        
        PortfolioMetricsResponse metricsResponse = new PortfolioMetricsResponse(
            accountId,
            new BigDecimal("100000.00"),
            new BigDecimal("0.00"),
            new BigDecimal("100000.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            Arrays.asList()
        );

        // STEP 2: Setup - Mock the service
        when(portfolioService.getPortfolioMetrics(accountId))
            .thenReturn(metricsResponse);

        // STEP 3: Act & Assert
        mockMvc.perform(get("/v1/accounts/{accountId}/metrics", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.totalUnrealizedPnL").value("0.00"))
            .andExpect(jsonPath("$.portfolioReturn").value("0.00"))
            .andExpect(jsonPath("$.positions.length()").value(0));
    }

    @Test
    void testGetPortfolioMetrics_AccountNotFound() throws Exception {
        // STEP 1: Setup - Create test data
        String accountId = "INVALID_ACC";

        // STEP 2: Setup - Mock the service to throw exception
        when(portfolioService.getPortfolioMetrics(accountId))
            .thenThrow(new AccountNotFoundException("Account not found: " + accountId));

        // STEP 3: Act & Assert - Verify 404 Not Found
        mockMvc.perform(get("/v1/accounts/{accountId}/metrics", accountId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testGetPortfolioMetrics_MultiplePositionsWithMixedReturns() throws Exception {
        // STEP 1: Setup - Create test data with mixed gains and losses
        String accountId = "ACC777";
        
        PositionResponse gainPosition = new PositionResponse(
            accountId,
            "AAPL",
            100,
            new BigDecimal("150.00"),
            new BigDecimal("180.00"),
            new BigDecimal("18000.00"),
            new BigDecimal("3000.00")
        );
        
        PositionResponse lossPosition = new PositionResponse(
            accountId,
            "AMD",
            50,
            new BigDecimal("200.00"),
            new BigDecimal("180.00"),
            new BigDecimal("9000.00"),
            new BigDecimal("-1000.00")
        );
        
        PositionResponse breakEvenPosition = new PositionResponse(
            accountId,
            "INTC",
            200,
            new BigDecimal("50.00"),
            new BigDecimal("50.00"),
            new BigDecimal("10000.00"),
            new BigDecimal("0.00")
        );
        
        List<PositionResponse> positions = Arrays.asList(gainPosition, lossPosition, breakEvenPosition);
        
        PortfolioMetricsResponse metricsResponse = new PortfolioMetricsResponse(
            accountId,
            new BigDecimal("50000.00"),
            new BigDecimal("37000.00"),  // 18000 + 9000 + 10000
            new BigDecimal("87000.00"),  // 50000 + 37000
            new BigDecimal("2000.00"),   // 3000 - 1000 + 0
            new BigDecimal("2.30"),      // (2000/87000 * 100)
            positions
        );

        // STEP 2: Setup - Mock the service
        when(portfolioService.getPortfolioMetrics(accountId))
            .thenReturn(metricsResponse);

        // STEP 3: Act & Assert
        mockMvc.perform(get("/v1/accounts/{accountId}/metrics", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalUnrealizedPnL").value("2000.00"))
            .andExpect(jsonPath("$.portfolioReturn").value("2.30"))
            .andExpect(jsonPath("$.positions.length()").value(3))
            .andExpect(jsonPath("$.positions[0].unrealizedPnL").value("3000.00"))
            .andExpect(jsonPath("$.positions[1].unrealizedPnL").value("-1000.00"))
            .andExpect(jsonPath("$.positions[2].unrealizedPnL").value("0.00"));
    }
}
