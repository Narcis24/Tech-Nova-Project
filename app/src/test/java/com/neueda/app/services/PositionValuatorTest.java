package com.neueda.app.services;

import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Position;
import com.neueda.app.models.Price;
import com.neueda.app.repositories.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PositionValuatorTest {

    private PriceRepository priceRepository;
    private PositionValuator positionValuator;
    private Account account;

    @BeforeEach
    void setUp() {
        priceRepository = mock(PriceRepository.class);
        positionValuator = new PositionValuator(priceRepository);
        account = new Account("ACC1", "Test Holder", new BigDecimal("1000"),
            AccountStatus.ACTIVE, LocalDateTime.now());
    }

    private Position position(String symbol, int quantity, String averageCost) {
        Instrument instrument = new Instrument(symbol, symbol + " Inc.", AssetClass.EQUITY, "USD", true);
        return new Position(account, instrument, quantity, new BigDecimal(averageCost));
    }

    private static Price price(String symbol, String close) {
        return new Price(symbol, LocalDate.now(), new BigDecimal(close));
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void valueUsesLatestPrice() {
        // 10 AAPL bought at 100, now trading at 150
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("AAPL"))
            .thenReturn(Optional.of(price("AAPL", "150")));

        PositionResponse response = positionValuator.value(position("AAPL", 10, "100"));

        assertEquals("ACC1", response.getAccountId());
        assertEquals("AAPL", response.getSymbol());
        assertEquals(10, response.getQuantity());
        assertAmount("100", response.getAverageCost());
        assertAmount("150", response.getCurrentPrice());
        assertAmount("1500", response.getMarketValue());
        assertAmount("500", response.getUnrealizedPnL());
    }

    @Test
    void valueWithoutPriceIsValuedAtZero() {
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("AAPL")).thenReturn(Optional.empty());

        PositionResponse response = positionValuator.value(position("AAPL", 10, "100"));

        assertAmount("0", response.getCurrentPrice());
        assertAmount("0", response.getMarketValue());
        assertAmount("-1000", response.getUnrealizedPnL());
    }

    @Test
    void valueAllOfNoPositionsSkipsThePriceQuery() {
        List<PositionResponse> responses = positionValuator.valueAll(List.of());

        assertTrue(responses.isEmpty());
        verifyNoInteractions(priceRepository);
    }

    @Test
    void valueAllFetchesEveryPriceInOneQuery() {
        // 10 AAPL at 100 now 150 (+500), 5 MSFT at 300 now 280 (-100)
        when(priceRepository.findLatestBySymbols(List.of("AAPL", "MSFT")))
            .thenReturn(List.of(price("AAPL", "150"), price("MSFT", "280")));

        List<PositionResponse> responses = positionValuator.valueAll(
            List.of(position("AAPL", 10, "100"), position("MSFT", 5, "300")));

        assertEquals(2, responses.size());
        assertEquals("AAPL", responses.get(0).getSymbol());
        assertAmount("1500", responses.get(0).getMarketValue());
        assertAmount("500", responses.get(0).getUnrealizedPnL());
        assertEquals("MSFT", responses.get(1).getSymbol());
        assertAmount("1400", responses.get(1).getMarketValue());
        assertAmount("-100", responses.get(1).getUnrealizedPnL());

        // One batch query, never the per-symbol lookup (the old N+1 pattern)
        verify(priceRepository, times(1)).findLatestBySymbols(anyCollection());
        verify(priceRepository, never()).findFirstBySymbolOrderByTradeDateDesc(anyString());
    }

    @Test
    void valueAllValuesOnlyTheUnpricedPositionAtZero() {
        // No price data for TSLA, so the batch query leaves it out
        when(priceRepository.findLatestBySymbols(List.of("AAPL", "TSLA")))
            .thenReturn(List.of(price("AAPL", "150")));

        List<PositionResponse> responses = positionValuator.valueAll(
            List.of(position("AAPL", 10, "100"), position("TSLA", 2, "250")));

        assertAmount("1500", responses.get(0).getMarketValue());
        assertAmount("0", responses.get(1).getCurrentPrice());
        assertAmount("0", responses.get(1).getMarketValue());
        assertAmount("-500", responses.get(1).getUnrealizedPnL());
    }
}
