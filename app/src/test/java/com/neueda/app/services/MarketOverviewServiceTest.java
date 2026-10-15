package com.neueda.app.services;

import com.neueda.app.enums.AssetClass;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Price;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.PriceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketOverviewServiceTest {
    @Test void calculatesSessionChangeAndOrdersSparklineChronologically() {
        var prices = mock(PriceRepository.class);
        var instruments = mock(InstrumentRepository.class);
        when(instruments.findAll()).thenReturn(List.of(new Instrument("SPY", "S&P 500", AssetClass.ETF, "USD", true)));
        var day = LocalDate.of(2026, 10, 15);
        when(prices.findRecentPrices()).thenReturn(List.of(new Price("SPY", day, new BigDecimal("110")), new Price("SPY", day.minusDays(1), new BigDecimal("100"))));
        var result = new MarketOverviewService(prices, instruments).overview().getFirst();
        assertEquals(new BigDecimal("10"), result.change());
        assertEquals(0, new BigDecimal("0.1").compareTo(result.changePercent()));
        assertEquals(List.of(new BigDecimal("100"), new BigDecimal("110")), result.history());
        assertEquals(day, result.priceDate());
    }
    @Test void leavesChangeUnknownWithOnlyOneSessionAndOmitsUnpricedInstruments() {
        var prices = mock(PriceRepository.class);
        var instruments = mock(InstrumentRepository.class);
        when(instruments.findAll()).thenReturn(List.of(new Instrument("SPY", "S&P 500", AssetClass.ETF, "USD", true), new Instrument("QQQ", "Nasdaq", AssetClass.ETF, "USD", true)));
        when(prices.findRecentPrices()).thenReturn(List.of(new Price("SPY", LocalDate.now(), BigDecimal.TEN)));
        var result = new MarketOverviewService(prices, instruments).overview();
        assertEquals(1, result.size()); assertNull(result.getFirst().changePercent());
    }
}
