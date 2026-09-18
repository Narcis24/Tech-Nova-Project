package com.neueda.app.service;

import com.neueda.app.exceptions.TradingException;
import com.neueda.app.repository.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

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
    void testGetCurrentPriceSuccess() {
        // Arrange
        String symbol = "AAPL";
        BigDecimal expectedPrice = new BigDecimal("150.50");
        when(priceRepository.findLatestPrice(symbol))
            .thenReturn(Optional.of(expectedPrice));

        // Act
        BigDecimal result = priceService.getCurrentPrice(symbol);

        // Assert
        assertEquals(expectedPrice, result);
        verify(priceRepository, times(1)).findLatestPrice(symbol);
    }

    @Test
    void testGetCurrentPriceNotFound() {
        // Arrange
        String symbol = "UNKNOWN";
        when(priceRepository.findLatestPrice(symbol))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(TradingException.class, () -> {
            priceService.getCurrentPrice(symbol);
        });
        verify(priceRepository, times(1)).findLatestPrice(symbol);
    }

    @Test
    void testGetCurrentPriceMultipleSymbols() {
        // Arrange
        when(priceRepository.findLatestPrice("AAPL"))
            .thenReturn(Optional.of(new BigDecimal("150.50")));
        when(priceRepository.findLatestPrice("MSFT"))
            .thenReturn(Optional.of(new BigDecimal("320.75")));

        // Act & Assert
        assertEquals(new BigDecimal("150.50"), priceService.getCurrentPrice("AAPL"));
        assertEquals(new BigDecimal("320.75"), priceService.getCurrentPrice("MSFT"));
        verify(priceRepository, times(2)).findLatestPrice(anyString());
    }
}
