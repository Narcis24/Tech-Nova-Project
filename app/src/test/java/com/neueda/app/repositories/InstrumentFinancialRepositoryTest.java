package com.neueda.app.repositories;

import com.neueda.app.enums.AssetClass;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.InstrumentFinancial;
import com.neueda.app.models.InstrumentFinancial.PeriodType;
import com.neueda.app.models.InstrumentProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The fundamentals tables against a real (H2) database. */
@DataJpaTest
@ActiveProfiles("test")
class InstrumentFinancialRepositoryTest {

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private InstrumentProfileRepository profileRepository;

    @Autowired
    private InstrumentFinancialRepository financialRepository;

    @Test
    void readsAProfileAndItsPeriodsGroupedByTypeOldestFirst() {
        instrumentRepository.save(new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true));
        instrumentRepository.save(new Instrument("MSFT", "Microsoft", AssetClass.EQUITY, "USD", true));
        profileRepository.save(InstrumentProfile.builder().symbol("AAPL").quoteType("EQUITY")
            .marketCap(new BigDecimal("5081985449984.00")).updatedAt(LocalDateTime.of(2026, 10, 23, 12, 0)).build());
        financialRepository.saveAll(List.of(
            period("AAPL", PeriodType.QUARTERLY, LocalDate.of(2026, 6, 30)),
            period("AAPL", PeriodType.ANNUAL, LocalDate.of(2025, 9, 30)),
            period("AAPL", PeriodType.QUARTERLY, LocalDate.of(2026, 3, 31)),
            period("MSFT", PeriodType.QUARTERLY, LocalDate.of(2026, 6, 30))));

        assertEquals(0, new BigDecimal("5081985449984").compareTo(
            profileRepository.findById("AAPL").orElseThrow().getMarketCap()));
        List<InstrumentFinancial> periods = financialRepository.findBySymbolOrderByPeriodTypeAscPeriodEndAsc("AAPL");
        assertEquals(List.of("ANNUAL 2025-09-30", "QUARTERLY 2026-03-31", "QUARTERLY 2026-06-30"),
            periods.stream().map(p -> p.getPeriodType() + " " + p.getPeriodEnd()).toList());
    }

    private static InstrumentFinancial period(String symbol, PeriodType type, LocalDate end) {
        return InstrumentFinancial.builder().symbol(symbol).periodType(type).periodEnd(end)
            .revenue(new BigDecimal("100")).build();
    }
}
