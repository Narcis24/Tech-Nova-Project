package com.neueda.app.services;

import com.neueda.app.dtos.FundamentalsResponse;
import com.neueda.app.models.InstrumentFinancial;
import com.neueda.app.models.InstrumentFinancial.PeriodType;
import com.neueda.app.models.InstrumentProfile;
import com.neueda.app.repositories.InstrumentFinancialRepository;
import com.neueda.app.repositories.InstrumentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FundamentalsServiceTest {

    private InstrumentProfileRepository profileRepository;
    private InstrumentFinancialRepository financialRepository;
    private FundamentalsService service;

    @BeforeEach
    void setUp() {
        profileRepository = mock(InstrumentProfileRepository.class);
        financialRepository = mock(InstrumentFinancialRepository.class);
        service = new FundamentalsService(profileRepository, financialRepository);
    }

    @Test
    void combinesTheProfileWithQuartersAndYearsOldestFirst() {
        when(profileRepository.findById("AAPL")).thenReturn(Optional.of(InstrumentProfile.builder()
            .symbol("AAPL").quoteType("EQUITY").sector("Technology").epsTtm(new BigDecimal("8.72"))
            .dividendYield(new BigDecimal("0.0031")).updatedAt(LocalDateTime.of(2026, 10, 23, 12, 0)).build()));
        when(financialRepository.findBySymbolOrderByPeriodTypeAscPeriodEndAsc("AAPL")).thenReturn(List.of(
            period(PeriodType.QUARTERLY, LocalDate.of(2026, 3, 31), "111184000000"),
            period(PeriodType.QUARTERLY, LocalDate.of(2026, 6, 30), "109417000000"),
            period(PeriodType.ANNUAL, LocalDate.of(2025, 9, 30), "416161000000")));

        FundamentalsResponse aapl = service.getFundamentals("AAPL").orElseThrow();

        assertEquals("Technology", aapl.sector());
        assertEquals(new BigDecimal("8.72"), aapl.epsTtm());
        assertEquals(Instant.parse("2026-10-23T12:00:00Z"), aapl.updatedAt());
        assertEquals(List.of(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 6, 30)),
            aapl.quarters().stream().map(FundamentalsResponse.FinancialPeriod::periodEnd).toList());
        assertEquals(new BigDecimal("416161000000"), aapl.years().getFirst().revenue());
    }

    @Test
    void aFundHasNoPeriods() {
        when(profileRepository.findById("SPY")).thenReturn(Optional.of(InstrumentProfile.builder()
            .symbol("SPY").quoteType("ETF").totalAssets(new BigDecimal("809216114688"))
            .updatedAt(LocalDateTime.of(2026, 10, 23, 12, 0)).build()));

        FundamentalsResponse spy = service.getFundamentals("SPY").orElseThrow();

        assertEquals("ETF", spy.quoteType());
        assertTrue(spy.quarters().isEmpty());
        assertTrue(spy.years().isEmpty());
    }

    @Test
    void nothingUntilThePipelineLoadedTheSymbol() {
        when(profileRepository.findById("TSLA")).thenReturn(Optional.empty());

        assertTrue(service.getFundamentals("TSLA").isEmpty());
        verifyNoInteractions(financialRepository);
    }

    private static InstrumentFinancial period(PeriodType type, LocalDate end, String revenue) {
        return InstrumentFinancial.builder().symbol("AAPL").periodType(type).periodEnd(end)
            .revenue(new BigDecimal(revenue)).build();
    }
}
