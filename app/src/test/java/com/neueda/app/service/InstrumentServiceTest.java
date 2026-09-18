package com.neueda.app.service;

import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.dto.InstrumentResponse;
import com.neueda.app.model.Instrument;
import com.neueda.app.repository.InstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstrumentServiceTest {

    private InstrumentRepository instrumentRepository;
    private InstrumentService instrumentService;

    @BeforeEach
    void setUp() {
        instrumentRepository = mock(InstrumentRepository.class);
        instrumentService = new InstrumentService(instrumentRepository);
    }

    @Test
    void testIsTradableSuccess() {
        // Arrange
        String symbol = "AAPL";
        Instrument instrument = new Instrument(symbol, "Apple Inc.", AssetClass.EQUITY, "USD", true);
        
        when(instrumentRepository.findBySymbol(symbol))
            .thenReturn(Optional.of(instrument));

        // Act
        boolean result = instrumentService.isTradable(symbol);

        // Assert
        assertTrue(result);
        verify(instrumentRepository, times(1)).findBySymbol(symbol);
    }

    @Test
    void testIsTradableNotTradable() {
        // Arrange
        String symbol = "AAPL";
        Instrument instrument = new Instrument(symbol, "Apple Inc.", AssetClass.EQUITY, "USD", false);
        
        when(instrumentRepository.findBySymbol(symbol))
            .thenReturn(Optional.of(instrument));

        // Act
        boolean result = instrumentService.isTradable(symbol);

        // Assert
        assertFalse(result);
        verify(instrumentRepository, times(1)).findBySymbol(symbol);
    }

    @Test
    void testIsTradableNotFound() {
        // Arrange
        String symbol = "UNKNOWN";
        when(instrumentRepository.findBySymbol(symbol))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InstrumentNotFoundException.class, () -> {
            instrumentService.isTradable(symbol);
        });
        verify(instrumentRepository, times(1)).findBySymbol(symbol);
    }

    @Test
    void testGetAllTradable() {
        // Arrange
        List<Instrument> instruments = new ArrayList<>();
        instruments.add(new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true));
        instruments.add(new Instrument("MSFT", "Microsoft Corp.", AssetClass.EQUITY, "USD", true));
        
        when(instrumentRepository.findAllTradable())
            .thenReturn(instruments);

        // Act
        List<InstrumentResponse> result = instrumentService.getAllTradable();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(instrumentRepository, times(1)).findAllTradable();
    }

    @Test
    void testGetAllTradableEmpty() {
        // Arrange
        when(instrumentRepository.findAllTradable())
            .thenReturn(new ArrayList<>());

        // Act
        List<InstrumentResponse> result = instrumentService.getAllTradable();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
        verify(instrumentRepository, times(1)).findAllTradable();
    }

    @Test
    void testIsTradableMultipleSymbols() {
        // Arrange
        when(instrumentRepository.findBySymbol("AAPL"))
            .thenReturn(Optional.of(new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true)));
        when(instrumentRepository.findBySymbol("GOOG"))
            .thenReturn(Optional.of(new Instrument("GOOG", "Google LLC", AssetClass.EQUITY, "USD", true)));

        // Act & Assert
        assertTrue(instrumentService.isTradable("AAPL"));
        assertTrue(instrumentService.isTradable("GOOG"));
        verify(instrumentRepository, times(2)).findBySymbol(anyString());
    }
}
