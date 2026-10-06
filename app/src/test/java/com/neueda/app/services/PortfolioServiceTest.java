package com.neueda.app.services;

import com.neueda.app.dtos.PortfolioMetricsResponse;
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
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
        portfolioService = new PortfolioService(accountRepository, positionRepository, priceRepository);

        // 1,000 cash + 10 AAPL bought at 100, now trading at 150
        Account account = new Account("ACC1", "Test Holder", new BigDecimal("1000"),
            AccountStatus.ACTIVE, LocalDateTime.now());
        Instrument aapl = new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
        Position position = new Position(account, aapl, 10, new BigDecimal("100"));

        when(accountRepository.findById("ACC1")).thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId("ACC1")).thenReturn(List.of(position));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("AAPL"))
            .thenReturn(Optional.of(new Price("AAPL", LocalDate.now(), new BigDecimal("150"))));
    }

    @Test
    void snapshotValuesPositionsAtLatestPrice() {
        PortfolioSnapshotResponse snapshot = portfolioService.getPortfolioSnapshot("ACC1");

        assertEquals(0, new BigDecimal("150").compareTo(snapshot.getPositions().get(0).getCurrentPrice()));
        assertEquals(0, new BigDecimal("1500").compareTo(snapshot.getTotalMarketValue()));
        assertEquals(0, new BigDecimal("2500").compareTo(snapshot.getTotalPortfolioValue()));
        assertEquals(0, new BigDecimal("500").compareTo(snapshot.getPositions().get(0).getUnrealizedPnL()));
    }

    @Test
    void metricsUseLatestPrice() {
        PortfolioMetricsResponse metrics = portfolioService.getPortfolioMetrics("ACC1");

        assertEquals(0, new BigDecimal("1500").compareTo(metrics.getTotalMarketValue()));
        assertEquals(0, new BigDecimal("500").compareTo(metrics.getTotalUnrealizedPnL()));
        assertEquals(0, new BigDecimal("2500").compareTo(metrics.getTotalPortfolioValue()));
    }

    @Test
    void positionWithoutPriceIsValuedAtZero() {
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("AAPL")).thenReturn(Optional.empty());

        PortfolioSnapshotResponse snapshot = portfolioService.getPortfolioSnapshot("ACC1");

        assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.getTotalMarketValue()));
    }
}
