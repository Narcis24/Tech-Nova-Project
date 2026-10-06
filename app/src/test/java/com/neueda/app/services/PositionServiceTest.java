package com.neueda.app.services;

import com.neueda.app.dtos.PositionMetricsResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.exceptions.TradingException;
import com.neueda.app.models.Position;
import com.neueda.app.models.Price;
import com.neueda.app.repositories.PositionRepository;
import com.neueda.app.repositories.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PositionServiceTest {

    private PositionRepository positionRepository;
    private PriceRepository priceRepository;
    private PositionService positionService;

    @BeforeEach
    void setUp() {
        positionRepository = mock(PositionRepository.class);
        priceRepository = mock(PriceRepository.class);
        positionService = new PositionService(positionRepository, priceRepository);
    }

    // ========== getPosition Tests ==========
    @Test
    void testGetPositionSuccess() {
        // Arrange
        String accountId = "ACC001";
        String symbol = "AAPL";
        
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn(symbol);
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));

        Price price = new Price(symbol, LocalDate.now(), new BigDecimal("160.00"));

        when(positionRepository.findByAccountIdAndSymbol(accountId, symbol))
            .thenReturn(Optional.of(mockPosition));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol))
            .thenReturn(Optional.of(price));

        // Act
        PositionResponse result = positionService.getPosition(accountId, symbol);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(symbol, result.getSymbol());
        assertEquals(100, result.getQuantity());
        verify(positionRepository, times(1)).findByAccountIdAndSymbol(accountId, symbol);
    }

    @Test
    void testGetPositionNotFound() {
        // Arrange
        String accountId = "ACC001";
        String symbol = "AAPL";

        when(positionRepository.findByAccountIdAndSymbol(accountId, symbol))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(TradingException.class, () -> {
            positionService.getPosition(accountId, symbol);
        });
    }

    @Test
    void testGetPositionPriceNotFound() {
        // Arrange
        String accountId = "ACC001";
        String symbol = "AAPL";
        
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn(symbol);
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(BigDecimal.ZERO);
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(BigDecimal.ZERO);

        when(positionRepository.findByAccountIdAndSymbol(accountId, symbol))
            .thenReturn(Optional.of(mockPosition));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol))
            .thenReturn(Optional.empty());

        // Act
        PositionResponse result = positionService.getPosition(accountId, symbol);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(symbol, result.getSymbol());
    }

    @Test
    void testGetPositionMultipleSymbols() {
        // Arrange
        String accountId = "ACC001";
        
        Position mockPos1 = mock(Position.class);
        Position mockPos2 = mock(Position.class);

        when(mockPos1.getAccountId()).thenReturn(accountId);
        when(mockPos1.getSymbol()).thenReturn("AAPL");
        when(mockPos1.getQuantity()).thenReturn(100);
        when(mockPos1.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPos1.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPos1.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));

        when(mockPos2.getAccountId()).thenReturn(accountId);
        when(mockPos2.getSymbol()).thenReturn("GOOGL");
        when(mockPos2.getQuantity()).thenReturn(50);
        when(mockPos2.getAverageCost()).thenReturn(new BigDecimal("2800.00"));
        when(mockPos2.getMarketValue(any())).thenReturn(new BigDecimal("150000.00"));
        when(mockPos2.getUnrealizedPnL(any())).thenReturn(new BigDecimal("5000.00"));

        when(positionRepository.findByAccountIdAndSymbol(accountId, "AAPL"))
            .thenReturn(Optional.of(mockPos1));
        when(positionRepository.findByAccountIdAndSymbol(accountId, "GOOGL"))
            .thenReturn(Optional.of(mockPos2));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc(anyString()))
            .thenReturn(Optional.of(new Price("SYMBOL", LocalDate.now(), new BigDecimal("100.00"))));

        // Act
        PositionResponse result1 = positionService.getPosition(accountId, "AAPL");
        PositionResponse result2 = positionService.getPosition(accountId, "GOOGL");

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals("AAPL", result1.getSymbol());
        assertEquals("GOOGL", result2.getSymbol());
    }

    // ========== getAccountPositions Tests ==========
    @Test
    void testGetAccountPositionsSuccess() {
        // Arrange
        String accountId = "ACC001";
        
        List<Position> positions = new ArrayList<>();
        Position mockPos = mock(Position.class);
        when(mockPos.getAccountId()).thenReturn(accountId);
        when(mockPos.getSymbol()).thenReturn("AAPL");
        when(mockPos.getQuantity()).thenReturn(100);
        when(mockPos.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPos.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPos.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));
        positions.add(mockPos);

        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("AAPL"))
            .thenReturn(Optional.of(new Price("AAPL", LocalDate.now(), new BigDecimal("160.00"))));

        // Act
        List<PositionResponse> result = positionService.getAccountPositions(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(positionRepository, times(1)).findByAccountId(accountId);
    }

    @Test
    void testGetAccountPositionsEmpty() {
        // Arrange
        String accountId = "ACC001";
        
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(new ArrayList<>());

        // Act
        List<PositionResponse> result = positionService.getAccountPositions(accountId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAccountPositionsMultiple() {
        // Arrange
        String accountId = "ACC001";
        
        List<Position> positions = new ArrayList<>();
        Position mockPos1 = mock(Position.class);
        Position mockPos2 = mock(Position.class);
        Position mockPos3 = mock(Position.class);

        when(mockPos1.getAccountId()).thenReturn(accountId);
        when(mockPos1.getSymbol()).thenReturn("AAPL");
        when(mockPos1.getQuantity()).thenReturn(100);
        when(mockPos1.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPos1.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPos1.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));

        when(mockPos2.getAccountId()).thenReturn(accountId);
        when(mockPos2.getSymbol()).thenReturn("GOOGL");
        when(mockPos2.getQuantity()).thenReturn(50);
        when(mockPos2.getAverageCost()).thenReturn(new BigDecimal("2800.00"));
        when(mockPos2.getMarketValue(any())).thenReturn(new BigDecimal("150000.00"));
        when(mockPos2.getUnrealizedPnL(any())).thenReturn(new BigDecimal("5000.00"));

        when(mockPos3.getAccountId()).thenReturn(accountId);
        when(mockPos3.getSymbol()).thenReturn("MSFT");
        when(mockPos3.getQuantity()).thenReturn(200);
        when(mockPos3.getAverageCost()).thenReturn(new BigDecimal("300.00"));
        when(mockPos3.getMarketValue(any())).thenReturn(new BigDecimal("65000.00"));
        when(mockPos3.getUnrealizedPnL(any())).thenReturn(new BigDecimal("2000.00"));

        positions.add(mockPos1);
        positions.add(mockPos2);
        positions.add(mockPos3);

        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc(anyString()))
            .thenReturn(Optional.of(new Price("SYMBOL", LocalDate.now(), new BigDecimal("100.00"))));

        // Act
        List<PositionResponse> result = positionService.getAccountPositions(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(accountId, result.get(0).getAccountId());
        assertEquals(accountId, result.get(1).getAccountId());
        assertEquals(accountId, result.get(2).getAccountId());
    }

    @Test
    void testGetAccountPositionsPriceNotFound() {
        // Arrange
        String accountId = "ACC001";
        
        List<Position> positions = new ArrayList<>();
        Position mockPos = mock(Position.class);
        when(mockPos.getAccountId()).thenReturn(accountId);
        when(mockPos.getSymbol()).thenReturn("AAPL");
        when(mockPos.getQuantity()).thenReturn(100);
        when(mockPos.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPos.getMarketValue(any())).thenReturn(BigDecimal.ZERO);
        when(mockPos.getUnrealizedPnL(any())).thenReturn(BigDecimal.ZERO);
        positions.add(mockPos);

        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("AAPL"))
            .thenReturn(Optional.empty());

        // Act
        List<PositionResponse> result = positionService.getAccountPositions(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    // ========== getPositionMetrics Tests ==========
    @Test
    void testGetPositionMetricsSuccess() {
        // Arrange
        String accountId = "ACC001";
        String symbol = "AAPL";
        
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn(symbol);
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));

        Price price = new Price(symbol, LocalDate.now(), new BigDecimal("160.00"));

        when(positionRepository.findByAccountIdAndSymbol(accountId, symbol))
            .thenReturn(Optional.of(mockPosition));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol))
            .thenReturn(Optional.of(price));

        // Act
        PositionMetricsResponse result = positionService.getPositionMetrics(accountId, symbol);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(symbol, result.getSymbol());
        assertEquals(100, result.getQuantity());
        assertEquals(new BigDecimal("150.00"), result.getAverageCost());
        assertEquals(new BigDecimal("16000.00"), result.getMarketValue());
        assertEquals(new BigDecimal("1000.00"), result.getUnrealizedPnL());
        assertNotNull(result.getReturnPercentage());
        verify(positionRepository, times(1)).findByAccountIdAndSymbol(accountId, symbol);
    }

    @Test
    void testGetPositionMetricsNotFound() {
        // Arrange
        String accountId = "ACC001";
        String symbol = "AAPL";

        when(positionRepository.findByAccountIdAndSymbol(accountId, symbol))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(TradingException.class, () -> {
            positionService.getPositionMetrics(accountId, symbol);
        });
    }

    @Test
    void testGetPositionMetricsPriceNotFound() {
        // Arrange
        String accountId = "ACC001";
        String symbol = "AAPL";
        
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn(symbol);
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(BigDecimal.ZERO);
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(BigDecimal.ZERO);

        when(positionRepository.findByAccountIdAndSymbol(accountId, symbol))
            .thenReturn(Optional.of(mockPosition));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol))
            .thenReturn(Optional.empty());

        // Act
        PositionMetricsResponse result = positionService.getPositionMetrics(accountId, symbol);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(symbol, result.getSymbol());
    }

    @Test
    void testGetPositionMetricsPositiveReturn() {
        // Arrange
        String accountId = "ACC001";
        String symbol = "AAPL";
        
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn(symbol);
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("16500.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1500.00"));

        Price price = new Price(symbol, LocalDate.now(), new BigDecimal("165.00"));

        when(positionRepository.findByAccountIdAndSymbol(accountId, symbol))
            .thenReturn(Optional.of(mockPosition));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol))
            .thenReturn(Optional.of(price));

        // Act
        PositionMetricsResponse result = positionService.getPositionMetrics(accountId, symbol);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getReturnPercentage());
        assertTrue(result.getReturnPercentage().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testGetPositionMetricsNegativeReturn() {
        // Arrange
        String accountId = "ACC001";
        String symbol = "AAPL";
        
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn(symbol);
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("14500.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("-1500.00"));

        Price price = new Price(symbol, LocalDate.now(), new BigDecimal("145.00"));

        when(positionRepository.findByAccountIdAndSymbol(accountId, symbol))
            .thenReturn(Optional.of(mockPosition));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol))
            .thenReturn(Optional.of(price));

        // Act
        PositionMetricsResponse result = positionService.getPositionMetrics(accountId, symbol);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getReturnPercentage());
        assertTrue(result.getReturnPercentage().compareTo(BigDecimal.ZERO) < 0);
    }

    @Test
    void testGetPositionMetricsZeroQuantity() {
        // Arrange
        String accountId = "ACC001";
        String symbol = "AAPL";
        
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn(symbol);
        when(mockPosition.getQuantity()).thenReturn(0);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(BigDecimal.ZERO);
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(BigDecimal.ZERO);

        Price price = new Price(symbol, LocalDate.now(), new BigDecimal("160.00"));

        when(positionRepository.findByAccountIdAndSymbol(accountId, symbol))
            .thenReturn(Optional.of(mockPosition));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol))
            .thenReturn(Optional.of(price));

        // Act
        PositionMetricsResponse result = positionService.getPositionMetrics(accountId, symbol);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getQuantity());
        assertEquals(BigDecimal.ZERO, result.getReturnPercentage());
    }

    @Test
    void testGetPositionMetricsLargePosition() {
        // Arrange
        String accountId = "ACC001";
        String symbol = "AAPL";
        
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn(symbol);
        when(mockPosition.getQuantity()).thenReturn(10000);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("1650000.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("100000.00"));

        Price price = new Price(symbol, LocalDate.now(), new BigDecimal("165.00"));

        when(positionRepository.findByAccountIdAndSymbol(accountId, symbol))
            .thenReturn(Optional.of(mockPosition));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol))
            .thenReturn(Optional.of(price));

        // Act
        PositionMetricsResponse result = positionService.getPositionMetrics(accountId, symbol);

        // Assert
        assertNotNull(result);
        assertEquals(10000, result.getQuantity());
        assertEquals(new BigDecimal("1650000.00"), result.getMarketValue());
        assertTrue(result.getReturnPercentage().compareTo(BigDecimal.ZERO) > 0);
    }
}
