package com.neueda.app.services;

import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.models.Price;
import com.neueda.app.repositories.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriceServiceTest {

    private PriceRepository priceRepository;
    private PriceService priceService;

    @BeforeEach
    void setUp() {
        priceRepository = mock(PriceRepository.class);
        priceService = new PriceService(priceRepository);
    }

    @Test
    void testGetCurrentPriceReturnsLatestClose() {
        Price latest = new Price("AAPL", LocalDate.of(2026, 9, 22), new BigDecimal("237.870000"));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("AAPL"))
            .thenReturn(Optional.of(latest));

        assertEquals(new BigDecimal("237.870000"), priceService.getCurrentPrice("AAPL"));
    }

    @Test
    void testGetCurrentPriceThrowsWhenNoPriceData() {
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("UNKNOWN"))
            .thenReturn(Optional.empty());

        assertThrows(PriceNotFoundException.class, () -> priceService.getCurrentPrice("UNKNOWN"));
    }

    @Test
    void historyUsesLatestSessionAsItsYearBoundaryAndPreservesDates() {
        LocalDate latestDay = LocalDate.of(2026, 10, 14);
        Price latest = new Price("AAPL", latestDay, new BigDecimal("110"));
        Price earlier = new Price("AAPL", latestDay.minusDays(3), new BigDecimal("100"));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("AAPL")).thenReturn(Optional.of(latest));
        when(priceRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc("AAPL", latestDay.minusYears(1)))
            .thenReturn(List.of(earlier, latest));
        var history = priceService.getHistory("AAPL");
        assertEquals(List.of(earlier.getTradeDate(), latestDay), history.stream().map(p -> p.date()).toList());
        assertEquals(new BigDecimal("110"), history.getLast().close());
    }

    @Test
    void unknownSymbolsHaveEmptyHistory() {
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("UNKNOWN")).thenReturn(Optional.empty());
        assertTrue(priceService.getHistory("UNKNOWN").isEmpty());
        verify(priceRepository, never()).findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(anyString(), any());
    }

    @Test
    void testPriceRejectsNonPositivePrice() {
        LocalDate date = LocalDate.of(2026, 9, 22);
        assertThrows(IllegalArgumentException.class,
            () -> new Price("AAPL", date, BigDecimal.ZERO));
    }
}
