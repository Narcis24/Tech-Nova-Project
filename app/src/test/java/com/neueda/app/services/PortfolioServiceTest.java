package com.neueda.app.services;

import com.neueda.app.dtos.PortfolioMetricsResponse;
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Position;
import com.neueda.app.models.Price;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.PositionRepository;
import com.neueda.app.repositories.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioServiceTest {

    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private PriceRepository priceRepository;
    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        positionRepository = mock(PositionRepository.class);
        priceRepository = mock(PriceRepository.class);
        // Real valuator over the mocked prices, so these tests still check the valuation math
        portfolioService = new PortfolioService(accountRepository, positionRepository, new PositionValuator(priceRepository));
    }

    // ========== getPortfolioSnapshot Tests ==========
    @Test
    void testGetPortfolioSnapshotSuccess() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Position> positions = new ArrayList<>();
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn("AAPL");
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));
        positions.add(mockPosition);

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findLatestBySymbols(List.of("AAPL")))
            .thenReturn(List.of(new Price("AAPL", LocalDate.now(), new BigDecimal("160.00"))));

        // Act
        PortfolioSnapshotResponse result = portfolioService.getPortfolioSnapshot(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(new BigDecimal("10000.00"), result.getCashBalance());
        assertEquals(new BigDecimal("16000.00"), result.getTotalMarketValue());
        assertEquals(new BigDecimal("26000.00"), result.getTotalPortfolioValue());
        assertEquals(1, result.getPositions().size());
    }

    @Test
    void testGetPortfolioSnapshotAccountNotFound() {
        // Arrange
        String accountId = "UNKNOWN";
        when(accountRepository.findById(accountId))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            portfolioService.getPortfolioSnapshot(accountId);
        });
    }

    @Test
    void testGetPortfolioSnapshotNoPositions() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(new ArrayList<>());

        // Act
        PortfolioSnapshotResponse result = portfolioService.getPortfolioSnapshot(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(new BigDecimal("10000.00"), result.getCashBalance());
        assertEquals(BigDecimal.ZERO, result.getTotalMarketValue());
        assertEquals(new BigDecimal("10000.00"), result.getTotalPortfolioValue());
        assertTrue(result.getPositions().isEmpty());
    }

    @Test
    void testGetPortfolioSnapshotMultiplePositions() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("20000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

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

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findLatestBySymbols(anyList()))
            .thenReturn(List.of(
                new Price("AAPL", LocalDate.now(), new BigDecimal("100.00")),
                new Price("GOOGL", LocalDate.now(), new BigDecimal("100.00")),
                new Price("MSFT", LocalDate.now(), new BigDecimal("100.00"))));

        // Act
        PortfolioSnapshotResponse result = portfolioService.getPortfolioSnapshot(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(new BigDecimal("20000.00"), result.getCashBalance());
        assertEquals(new BigDecimal("231000.00"), result.getTotalMarketValue());
        assertEquals(new BigDecimal("251000.00"), result.getTotalPortfolioValue());
        assertEquals(3, result.getPositions().size());
    }

    @Test
    void testGetPortfolioSnapshotZeroCashBalance() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            BigDecimal.ZERO,
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Position> positions = new ArrayList<>();
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn("AAPL");
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));
        positions.add(mockPosition);

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findLatestBySymbols(List.of("AAPL")))
            .thenReturn(List.of(new Price("AAPL", LocalDate.now(), new BigDecimal("160.00"))));

        // Act
        PortfolioSnapshotResponse result = portfolioService.getPortfolioSnapshot(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getCashBalance());
        assertEquals(new BigDecimal("16000.00"), result.getTotalMarketValue());
        assertEquals(new BigDecimal("16000.00"), result.getTotalPortfolioValue());
    }

    // ========== getPortfolioMetrics Tests ==========
    @Test
    void testGetPortfolioMetricsSuccess() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Position> positions = new ArrayList<>();
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn("AAPL");
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));
        positions.add(mockPosition);

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findLatestBySymbols(List.of("AAPL")))
            .thenReturn(List.of(new Price("AAPL", LocalDate.now(), new BigDecimal("160.00"))));

        // Act
        PortfolioMetricsResponse result = portfolioService.getPortfolioMetrics(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(new BigDecimal("10000.00"), result.getCashBalance());
        assertEquals(new BigDecimal("16000.00"), result.getTotalMarketValue());
        assertEquals(new BigDecimal("1000.00"), result.getTotalUnrealizedPnL());
        assertNotNull(result.getPortfolioReturn());
        assertEquals(1, result.getPositions().size());
    }

    @Test
    void testGetPortfolioMetricsAccountNotFound() {
        // Arrange
        String accountId = "UNKNOWN";
        when(accountRepository.findById(accountId))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            portfolioService.getPortfolioMetrics(accountId);
        });
    }

    @Test
    void testGetPortfolioMetricsNoPositions() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(new ArrayList<>());

        // Act
        PortfolioMetricsResponse result = portfolioService.getPortfolioMetrics(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(new BigDecimal("10000.00"), result.getCashBalance());
        assertEquals(BigDecimal.ZERO, result.getTotalMarketValue());
        assertEquals(BigDecimal.ZERO, result.getTotalUnrealizedPnL());
        assertTrue(result.getPositions().isEmpty());
    }

    @Test
    void testGetPortfolioMetricsMultiplePositions() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("50000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Position> positions = new ArrayList<>();
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

        positions.add(mockPos1);
        positions.add(mockPos2);

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findLatestBySymbols(anyList()))
            .thenReturn(List.of(
                new Price("AAPL", LocalDate.now(), new BigDecimal("100.00")),
                new Price("GOOGL", LocalDate.now(), new BigDecimal("100.00"))));

        // Act
        PortfolioMetricsResponse result = portfolioService.getPortfolioMetrics(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(new BigDecimal("50000.00"), result.getCashBalance());
        assertEquals(new BigDecimal("166000.00"), result.getTotalMarketValue());
        assertEquals(new BigDecimal("6000.00"), result.getTotalUnrealizedPnL());
        assertEquals(2, result.getPositions().size());
    }

    @Test
    void testGetPortfolioMetricsNegativeUnrealizedPnL() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Position> positions = new ArrayList<>();
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn("AAPL");
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("160.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("14000.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("-2000.00"));
        positions.add(mockPosition);

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findLatestBySymbols(List.of("AAPL")))
            .thenReturn(List.of(new Price("AAPL", LocalDate.now(), new BigDecimal("140.00"))));

        // Act
        PortfolioMetricsResponse result = portfolioService.getPortfolioMetrics(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(new BigDecimal("-2000.00"), result.getTotalUnrealizedPnL());
        assertEquals(new BigDecimal("24000.00"), result.getTotalPortfolioValue());
    }

    @Test
    void testGetPortfolioMetricsWithZeroCashBalance() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            BigDecimal.ZERO,
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Position> positions = new ArrayList<>();
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn("AAPL");
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));
        positions.add(mockPosition);

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findLatestBySymbols(List.of("AAPL")))
            .thenReturn(List.of(new Price("AAPL", LocalDate.now(), new BigDecimal("160.00"))));

        // Act
        PortfolioMetricsResponse result = portfolioService.getPortfolioMetrics(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getCashBalance());
        assertEquals(new BigDecimal("16000.00"), result.getTotalPortfolioValue());
    }

    @Test
    void testGetPortfolioMetricsPortfolioReturnCalculation() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("50000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Position> positions = new ArrayList<>();
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn("AAPL");
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));
        positions.add(mockPosition);

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findLatestBySymbols(List.of("AAPL")))
            .thenReturn(List.of(new Price("AAPL", LocalDate.now(), new BigDecimal("160.00"))));

        // Act
        PortfolioMetricsResponse result = portfolioService.getPortfolioMetrics(accountId);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getPortfolioReturn());
        // Portfolio value = cash + market value = 50000 + 16000 = 66000
        // Portfolio return = totalUnrealizedPnL / (cash + positions cost)
        assertTrue(result.getPortfolioReturn().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testGetPortfolioSnapshotAndMetricsConsistency() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Position> positions = new ArrayList<>();
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn("AAPL");
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));
        positions.add(mockPosition);

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findLatestBySymbols(List.of("AAPL")))
            .thenReturn(List.of(new Price("AAPL", LocalDate.now(), new BigDecimal("160.00"))));

        // Act
        PortfolioSnapshotResponse snapshot = portfolioService.getPortfolioSnapshot(accountId);
        PortfolioMetricsResponse metrics = portfolioService.getPortfolioMetrics(accountId);

        // Assert - both should have same cash, market value, and total portfolio value
        assertEquals(snapshot.getCashBalance(), metrics.getCashBalance());
        assertEquals(snapshot.getTotalMarketValue(), metrics.getTotalMarketValue());
        assertEquals(snapshot.getTotalPortfolioValue(), metrics.getTotalPortfolioValue());
    }

    // ========== Valuation Tests (real Position, real PositionValuator) ==========

    /** 1,000 cash + 10 AAPL bought at 100, now trading at 150. */
    private void givenCashAndTenAaplBoughtAt100TradingAt150() {
        Account account = new Account("ACC1", "Test Holder", new BigDecimal("1000"),
            AccountStatus.ACTIVE, LocalDateTime.now());
        Instrument aapl = new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
        Position position = new Position(account, aapl, 10, new BigDecimal("100"));

        when(accountRepository.findById("ACC1")).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId("ACC1")).thenReturn(List.of(position));
        when(priceRepository.findLatestBySymbols(List.of("AAPL")))
            .thenReturn(List.of(new Price("AAPL", LocalDate.now(), new BigDecimal("150"))));
    }

    @Test
    void snapshotValuesPositionsAtLatestPrice() {
        givenCashAndTenAaplBoughtAt100TradingAt150();

        PortfolioSnapshotResponse snapshot = portfolioService.getPortfolioSnapshot("ACC1");

        assertEquals(0, new BigDecimal("150").compareTo(snapshot.getPositions().get(0).getCurrentPrice()));
        assertEquals(0, new BigDecimal("1500").compareTo(snapshot.getTotalMarketValue()));
        assertEquals(0, new BigDecimal("2500").compareTo(snapshot.getTotalPortfolioValue()));
        assertEquals(0, new BigDecimal("500").compareTo(snapshot.getPositions().get(0).getUnrealizedPnL()));
    }

    @Test
    void metricsUseLatestPrice() {
        givenCashAndTenAaplBoughtAt100TradingAt150();

        PortfolioMetricsResponse metrics = portfolioService.getPortfolioMetrics("ACC1");

        assertEquals(0, new BigDecimal("1500").compareTo(metrics.getTotalMarketValue()));
        assertEquals(0, new BigDecimal("500").compareTo(metrics.getTotalUnrealizedPnL()));
        assertEquals(0, new BigDecimal("2500").compareTo(metrics.getTotalPortfolioValue()));
    }

    @Test
    void positionWithoutPriceIsValuedAtZero() {
        givenCashAndTenAaplBoughtAt100TradingAt150();
        when(priceRepository.findLatestBySymbols(List.of("AAPL"))).thenReturn(List.of());

        PortfolioSnapshotResponse snapshot = portfolioService.getPortfolioSnapshot("ACC1");

        assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.getTotalMarketValue()));
    }
}
