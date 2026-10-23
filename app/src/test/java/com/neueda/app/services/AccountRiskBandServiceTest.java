package com.neueda.app.services;

import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.dtos.RiskLimitResponse;
import com.neueda.app.enums.LimitSource;
import com.neueda.app.enums.RiskBand;
import com.neueda.app.enums.RiskRule;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountRiskBandServiceTest {
    private final PortfolioService portfolioService = mock(PortfolioService.class);
    private final RiskLimitService riskLimitService = mock(RiskLimitService.class);
    private final com.neueda.app.repositories.InstrumentRepository instrumentRepository = mock(
        com.neueda.app.repositories.InstrumentRepository.class);
    private final AccountRiskBandService service = new AccountRiskBandService(
        portfolioService, riskLimitService, instrumentRepository);

    @Test
    void classify_Below75Percent_ReturnsLow() {
        givenPortfolio("1000", "740");
        givenSingleInstrumentLimit("100");

        assertEquals(RiskBand.LOW, service.classify("ACC-1"));
    }

    @Test
    void classify_At75Percent_ReturnsMedium() {
        givenPortfolio("1000", "750");
        givenSingleInstrumentLimit("100");

        assertEquals(RiskBand.MEDIUM, service.classify("ACC-1"));
    }

    @Test
    void classify_At100Percent_ReturnsHigh() {
        givenPortfolio("1000", "1000");
        givenSingleInstrumentLimit("100");

        assertEquals(RiskBand.HIGH, service.classify("ACC-1"));
    }

    @Test
    void classify_EmptyPortfolio_ReturnsUnknown() {
        when(portfolioService.getPortfolioSnapshot("ACC-1"))
            .thenReturn(new PortfolioSnapshotResponse("ACC-1", new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("1000"), List.of()));
        when(riskLimitService.getLimits("ACC-1"))
            .thenReturn(List.of(new RiskLimitResponse(RiskRule.SINGLE_INSTRUMENT, new BigDecimal("100"),
                "ABOVE", true, LimitSource.DEFAULT)));

        assertEquals(RiskBand.UNKNOWN, service.classify("ACC-1"));
    }

    private void givenPortfolio(String total, String positionValue) {
        PositionResponse position = new PositionResponse("ACC-1", "AAPL", 1,
            BigDecimal.ONE, BigDecimal.ONE, new BigDecimal(positionValue), BigDecimal.ZERO);
        when(portfolioService.getPortfolioSnapshot("ACC-1"))
            .thenReturn(new PortfolioSnapshotResponse("ACC-1", BigDecimal.ZERO,
                new BigDecimal(positionValue), new BigDecimal(total), List.of(position)));
    }

    private void givenSingleInstrumentLimit(String threshold) {
        when(riskLimitService.getLimits("ACC-1"))
            .thenReturn(List.of(new RiskLimitResponse(RiskRule.SINGLE_INSTRUMENT, new BigDecimal(threshold),
                "ABOVE", true, LimitSource.DEFAULT)));
    }
}
