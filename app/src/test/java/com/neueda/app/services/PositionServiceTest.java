package com.neueda.app.services;

import com.neueda.app.dtos.PositionMetricsResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Position;
import com.neueda.app.models.Price;
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

class PositionServiceTest {

    private PositionRepository positionRepository;
    private PriceRepository priceRepository;
    private PositionService positionService;

    @BeforeEach
    void setUp() {
        positionRepository = mock(PositionRepository.class);
        priceRepository = mock(PriceRepository.class);
        positionService = new PositionService(positionRepository, priceRepository);

        // 10 AAPL bought at 100, now trading at 150
        Account account = new Account("ACC1", "Test Holder", new BigDecimal("1000"),
            AccountStatus.ACTIVE, LocalDateTime.now());
        Instrument aapl = new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
        Position position = new Position(account, aapl, 10, new BigDecimal("100"));

        when(positionRepository.findByAccountIdAndSymbol("ACC1", "AAPL")).thenReturn(Optional.of(position));
        when(positionRepository.findByAccountId("ACC1")).thenReturn(List.of(position));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("AAPL"))
            .thenReturn(Optional.of(new Price("AAPL", LocalDate.now(), new BigDecimal("150"))));
    }

    @Test
    void getPositionUsesLatestPrice() {
        PositionResponse response = positionService.getPosition("ACC1", "AAPL");

        assertEquals(0, new BigDecimal("150").compareTo(response.getCurrentPrice()));
        assertEquals(0, new BigDecimal("1500").compareTo(response.getMarketValue()));
        assertEquals(0, new BigDecimal("500").compareTo(response.getUnrealizedPnL()));
    }

    @Test
    void getAccountPositionsUsesLatestPrice() {
        List<PositionResponse> responses = positionService.getAccountPositions("ACC1");

        assertEquals(0, new BigDecimal("150").compareTo(responses.get(0).getCurrentPrice()));
        assertEquals(0, new BigDecimal("1500").compareTo(responses.get(0).getMarketValue()));
    }

    @Test
    void getPositionMetricsUsesLatestPrice() {
        PositionMetricsResponse metrics = positionService.getPositionMetrics("ACC1", "AAPL");

        assertEquals(0, new BigDecimal("150").compareTo(metrics.getCurrentPrice()));
        assertEquals(0, new BigDecimal("500").compareTo(metrics.getUnrealizedPnL()));
        // 500 gain on a 1,000 cost basis
        assertEquals(0, new BigDecimal("0.5").compareTo(metrics.getReturnPercentage()));
    }
}
