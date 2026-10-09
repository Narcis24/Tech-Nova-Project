package com.neueda.app.services;

import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.dtos.InstrumentResponse;
import com.neueda.app.models.Instrument;
import com.neueda.app.repositories.InstrumentRepository;
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
    void testGetInstrumentSuccess() {
        // Arrange
        when(instrumentRepository.findBySymbol("AAPL"))
            .thenReturn(Optional.of(new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true)));

        // Act
        InstrumentResponse result = instrumentService.getInstrument("AAPL");

        // Assert
        assertEquals("AAPL", result.getSymbol());
        assertTrue(result.isTradable());
    }

    @Test
    void testGetInstrumentNotFound() {
        // Arrange
        when(instrumentRepository.findBySymbol("UNKNOWN"))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InstrumentNotFoundException.class, () -> instrumentService.getInstrument("UNKNOWN"));
    }

    @Test
    void testGetInstrumentsWithoutFilterReturnsAll() {
        // Arrange
        when(instrumentRepository.findAll()).thenReturn(List.of(
            new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true),
            new Instrument("OLD", "Delisted Co.", AssetClass.EQUITY, "USD", false)));

        // Act
        List<InstrumentResponse> result = instrumentService.getInstruments(null);

        // Assert
        assertEquals(2, result.size());
        verify(instrumentRepository, never()).findByTradable(anyBoolean());
    }

    @Test
    void testGetInstrumentsTradableOnly() {
        // Arrange
        List<Instrument> instruments = new ArrayList<>();
        instruments.add(new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true));
        instruments.add(new Instrument("MSFT", "Microsoft Corp.", AssetClass.EQUITY, "USD", true));

        when(instrumentRepository.findByTradable(true))
            .thenReturn(instruments);

        // Act
        List<InstrumentResponse> result = instrumentService.getInstruments(true);

        // Assert
        assertEquals(2, result.size());
        verify(instrumentRepository, times(1)).findByTradable(true);
        verify(instrumentRepository, never()).findAll();
    }

    @Test
    void testGetInstrumentsNotTradableOnly() {
        // Arrange
        when(instrumentRepository.findByTradable(false)).thenReturn(List.of(
            new Instrument("OLD", "Delisted Co.", AssetClass.EQUITY, "USD", false)));

        // Act
        List<InstrumentResponse> result = instrumentService.getInstruments(false);

        // Assert
        assertEquals(1, result.size());
        assertFalse(result.get(0).isTradable());
    }

    @Test
    void testGetInstrumentsEmpty() {
        // Arrange
        when(instrumentRepository.findByTradable(true))
            .thenReturn(new ArrayList<>());

        // Act
        List<InstrumentResponse> result = instrumentService.getInstruments(true);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }
}
