package com.neueda.app.repositories;

import com.neueda.app.enums.AssetClass;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Price;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The native price queries against a real (H2) database, so every column Price maps is selected. */
@DataJpaTest
@ActiveProfiles("test")
class PriceRepositoryTest {

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Test
    void recentPricesKeepTheLatest20BarsPerSymbolWithEveryColumn() {
        instrumentRepository.save(new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true));
        LocalDate first = LocalDate.of(2026, 9, 1);
        for (int day = 0; day < 25; day++) {
            BigDecimal close = BigDecimal.valueOf(100 + day);
            priceRepository.save(new Price("AAPL", first.plusDays(day), close.subtract(BigDecimal.ONE),
                close.add(BigDecimal.TEN), close.subtract(BigDecimal.TEN), close, 1_000L * day));
        }

        List<Price> recent = priceRepository.findRecentPrices();

        assertEquals(20, recent.size());
        Price latest = recent.stream().filter(p -> p.getTradeDate().equals(first.plusDays(24))).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("124").compareTo(latest.getPrice()));
        assertEquals(0, new BigDecimal("134").compareTo(latest.getHigh()));
        assertEquals(24_000L, latest.getVolume());
    }
}
