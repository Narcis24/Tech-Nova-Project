package com.neueda.app.services;

import com.neueda.app.models.Price;
import com.neueda.app.repositories.PriceRepository;
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
        Price price = new Price();
        when(priceRepository.findBySymbol(symbol))
            .thenReturn(Optional.of(price));

        // Act
        BigDecimal result = priceService.getCurrentPrice(symbol);

        // Assert
        assertEquals(BigDecimal.ZERO, result);
        verify(priceRepository, times(1)).findBySymbol(symbol);
    }

    @Test
    void testGetCurrentPriceNotFound() {
        // Arrange
        String symbol = "UNKNOWN";
        when(priceRepository.findBySymbol(symbol))
            .thenReturn(Optional.empty());

        // Act
        BigDecimal result = priceService.getCurrentPrice(symbol);

        // Assert
        assertEquals(BigDecimal.ZERO, result);
        verify(priceRepository, times(1)).findBySymbol(symbol);
    }

    @Test
    void testGetCurrentPriceMultipleSymbols() {
        // Arrange
        Price price1 = new Price();
        Price price2 = new Price();
        when(priceRepository.findBySymbol("AAPL"))
            .thenReturn(Optional.of(price1));
        when(priceRepository.findBySymbol("MSFT"))
            .thenReturn(Optional.of(price2));

        // Act & Assert
        assertEquals(BigDecimal.ZERO, priceService.getCurrentPrice("AAPL"));
        assertEquals(BigDecimal.ZERO, priceService.getCurrentPrice("MSFT"));
        verify(priceRepository, times(2)).findBySymbol(anyString());
    }
}
