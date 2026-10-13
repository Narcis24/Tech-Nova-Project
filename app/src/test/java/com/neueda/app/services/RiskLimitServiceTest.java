package com.neueda.app.services;

import com.neueda.app.dtos.RiskLimitResponse;
import com.neueda.app.dtos.UpdateRiskLimitRequest;
import com.neueda.app.enums.LimitSource;
import com.neueda.app.enums.RiskRule;
import com.neueda.app.models.AccountRiskLimit;
import com.neueda.app.models.AccountRiskLimitKey;
import com.neueda.app.models.RiskLimit;
import com.neueda.app.repositories.AccountRiskLimitRepository;
import com.neueda.app.repositories.RiskLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RiskLimitServiceTest {

    private static final String ACCOUNT = "ACC001";

    private RiskLimitRepository riskLimitRepository;
    private AccountRiskLimitRepository accountRiskLimitRepository;
    private RiskLimitService riskLimitService;

    private final RiskLimit singleDefault = new RiskLimit(RiskRule.SINGLE_INSTRUMENT, new BigDecimal("20"), "ABOVE");
    private final RiskLimit classDefault = new RiskLimit(RiskRule.ASSET_CLASS, new BigDecimal("60"), "ABOVE");
    private final RiskLimit cashDefault = new RiskLimit(RiskRule.CASH_FLOOR, new BigDecimal("20"), "BELOW");

    @BeforeEach
    void setUp() {
        riskLimitRepository = mock(RiskLimitRepository.class);
        accountRiskLimitRepository = mock(AccountRiskLimitRepository.class);
        riskLimitService = new RiskLimitService(riskLimitRepository, accountRiskLimitRepository);

        when(riskLimitRepository.findAll()).thenReturn(List.of(singleDefault, classDefault, cashDefault));
        when(riskLimitRepository.findById(RiskRule.SINGLE_INSTRUMENT)).thenReturn(Optional.of(singleDefault));
        when(riskLimitRepository.findById(RiskRule.CASH_FLOOR)).thenReturn(Optional.of(cashDefault));
        when(accountRiskLimitRepository.findByAccountId(ACCOUNT)).thenReturn(List.of());
        when(accountRiskLimitRepository.save(any(AccountRiskLimit.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ========== getLimits ==========

    @Test
    void returnsDefaultsWhenAccountHasNoOwnLimits() {
        List<RiskLimitResponse> limits = riskLimitService.getLimits(ACCOUNT);

        assertEquals(3, limits.size());
        for (RiskLimitResponse limit : limits) {
            assertEquals(LimitSource.DEFAULT, limit.getSource());
            assertTrue(limit.isEnabled());
        }
        RiskLimitResponse cash = find(limits, RiskRule.CASH_FLOOR);
        assertEquals(new BigDecimal("20"), cash.getThresholdPct());
        assertEquals("BELOW", cash.getDirection());
    }

    @Test
    void ownThresholdReplacesDefault() {
        when(accountRiskLimitRepository.findByAccountId(ACCOUNT)).thenReturn(List.of(
            new AccountRiskLimit(ACCOUNT, RiskRule.SINGLE_INSTRUMENT, new BigDecimal("30"), true)));

        RiskLimitResponse single = find(riskLimitService.getLimits(ACCOUNT), RiskRule.SINGLE_INSTRUMENT);

        assertEquals(new BigDecimal("30"), single.getThresholdPct());
        assertEquals(LimitSource.CUSTOM, single.getSource());
    }

    @Test
    void switchedOffRuleIsListedAsDisabled() {
        when(accountRiskLimitRepository.findByAccountId(ACCOUNT)).thenReturn(List.of(
            new AccountRiskLimit(ACCOUNT, RiskRule.CASH_FLOOR, null, false)));

        RiskLimitResponse cash = find(riskLimitService.getLimits(ACCOUNT), RiskRule.CASH_FLOOR);

        assertFalse(cash.isEnabled());
        assertEquals(new BigDecimal("20"), cash.getThresholdPct());
        assertEquals(LimitSource.DEFAULT, cash.getSource());
    }

    // ========== updateLimit ==========

    @Test
    void updateSavesThresholdAndReturnsCustom() {
        RiskLimitResponse result = riskLimitService.updateLimit(ACCOUNT, RiskRule.SINGLE_INSTRUMENT,
            new UpdateRiskLimitRequest(new BigDecimal("25"), null));

        verify(accountRiskLimitRepository).save(argThat(l ->
            l.getAccountId().equals(ACCOUNT) && l.getRule() == RiskRule.SINGLE_INSTRUMENT
                && l.getThresholdPct().compareTo(new BigDecimal("25")) == 0 && l.isEnabled()));
        assertEquals(new BigDecimal("25"), result.getThresholdPct());
        assertEquals(LimitSource.CUSTOM, result.getSource());
        assertTrue(result.isEnabled());
    }

    @Test
    void updateCanSwitchRuleOffKeepingDefaultThreshold() {
        RiskLimitResponse result = riskLimitService.updateLimit(ACCOUNT, RiskRule.CASH_FLOOR,
            new UpdateRiskLimitRequest(null, false));

        assertFalse(result.isEnabled());
        assertEquals(new BigDecimal("20"), result.getThresholdPct());
        assertEquals(LimitSource.DEFAULT, result.getSource());
    }

    @Test
    void updateFailsWhenRuleHasNoDefault() {
        when(riskLimitRepository.findById(RiskRule.ASSET_CLASS)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> riskLimitService.updateLimit(ACCOUNT, RiskRule.ASSET_CLASS,
            new UpdateRiskLimitRequest(new BigDecimal("50"), true)));
        verify(accountRiskLimitRepository, never()).save(any());
    }

    // ========== resetLimit ==========

    @Test
    void resetDeletesOwnLimitAndReturnsDefault() {
        RiskLimitResponse result = riskLimitService.resetLimit(ACCOUNT, RiskRule.SINGLE_INSTRUMENT);

        verify(accountRiskLimitRepository).deleteById(any(AccountRiskLimitKey.class));
        assertEquals(new BigDecimal("20"), result.getThresholdPct());
        assertEquals(LimitSource.DEFAULT, result.getSource());
        assertTrue(result.isEnabled());
    }

    private static RiskLimitResponse find(List<RiskLimitResponse> limits, RiskRule rule) {
        return limits.stream().filter(l -> l.getRule() == rule).findFirst().orElseThrow();
    }
}
