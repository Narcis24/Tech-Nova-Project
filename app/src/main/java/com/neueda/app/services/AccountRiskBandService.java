package com.neueda.app.services;

import com.neueda.app.dtos.AdminAccountResponse;
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.dtos.RiskLimitResponse;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.RiskBand;
import com.neueda.app.enums.RiskRule;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.repositories.InstrumentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AccountRiskBandService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MEDIUM_THRESHOLD = new BigDecimal("75");
    private static final BigDecimal HIGH_THRESHOLD = new BigDecimal("100");

    private final PortfolioService portfolioService;
    private final RiskLimitService riskLimitService;
    private final InstrumentRepository instrumentRepository;

    public AccountRiskBandService(PortfolioService portfolioService, RiskLimitService riskLimitService,
                                  InstrumentRepository instrumentRepository) {
        this.portfolioService = portfolioService;
        this.riskLimitService = riskLimitService;
        this.instrumentRepository = instrumentRepository;
    }

    public AdminAccountResponse classify(Account account) {
        return new AdminAccountResponse(account.getAccountId(), account.getHolderName(),
            account.getCashBalance(), account.getAccountStatus(), classify(account.getAccountId()));
    }

    public RiskBand classify(String accountId) {
        PortfolioSnapshotResponse snapshot = portfolioService.getPortfolioSnapshot(accountId);
        List<RiskLimitResponse> limits = riskLimitService.getLimits(accountId).stream()
            .filter(RiskLimitResponse::isEnabled)
            .toList();
        if (limits.isEmpty() || snapshot.getPositions().isEmpty()
                || snapshot.getPositions().stream().anyMatch(position -> position.getCurrentPrice().signum() == 0)
                || snapshot.getTotalPortfolioValue().signum() <= 0) {
            return RiskBand.UNKNOWN;
        }

        Map<RiskRule, RiskLimitResponse> limitByRule = new EnumMap<>(RiskRule.class);
        limits.forEach(limit -> limitByRule.put(limit.getRule(), limit));
        BigDecimal worstUtilization = BigDecimal.ZERO;
        BigDecimal total = snapshot.getTotalPortfolioValue();
        RiskLimitResponse instrumentLimit = limitByRule.get(RiskRule.SINGLE_INSTRUMENT);
        if (instrumentLimit != null) {
            BigDecimal largestPosition = snapshot.getPositions().stream()
                .map(PositionResponse::getMarketValue).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            worstUtilization = worstUtilization.max(utilization(largestPosition, total, instrumentLimit));
        }

        RiskLimitResponse assetLimit = limitByRule.get(RiskRule.ASSET_CLASS);
        if (assetLimit != null) {
            Map<String, BigDecimal> valuesByClass = valuesByAssetClass(snapshot.getPositions());
            BigDecimal largestClass = valuesByClass.values().stream()
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            worstUtilization = worstUtilization.max(utilization(largestClass, total, assetLimit));
        }

        RiskLimitResponse cashLimit = limitByRule.get(RiskRule.CASH_FLOOR);
        if (cashLimit != null) {
            worstUtilization = worstUtilization.max(utilizationForCash(snapshot, cashLimit));
        }
        return bandFor(worstUtilization);
    }

    private Map<String, BigDecimal> valuesByAssetClass(List<PositionResponse> positions) {
        Map<String, AssetClass> classes = new HashMap<>();
        for (Instrument instrument : instrumentRepository.findAllById(positions.stream()
                .map(PositionResponse::getSymbol).toList())) {
            classes.put(instrument.getSymbol(), instrument.getAssetClass());
        }
        Map<String, BigDecimal> values = new HashMap<>();
        for (PositionResponse position : positions) {
            AssetClass assetClass = classes.get(position.getSymbol());
            if (assetClass != null) {
                values.merge(assetClass.name(), position.getMarketValue(), BigDecimal::add);
            }
        }
        return values;
    }

    private BigDecimal utilization(BigDecimal value, BigDecimal total, RiskLimitResponse limit) {
        BigDecimal percentage = value.multiply(HUNDRED).divide(total, 6, RoundingMode.HALF_UP);
        return percentage.multiply(HUNDRED).divide(limit.getThresholdPct(), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal utilizationForCash(PortfolioSnapshotResponse snapshot, RiskLimitResponse limit) {
        BigDecimal cashPercentage = snapshot.getCashBalance().multiply(HUNDRED)
            .divide(snapshot.getTotalPortfolioValue(), 6, RoundingMode.HALF_UP);
        if ("BELOW".equals(limit.getDirection())) {
            if (cashPercentage.signum() == 0) {
                return HIGH_THRESHOLD;
            }
            return limit.getThresholdPct().multiply(HUNDRED).divide(cashPercentage, 6, RoundingMode.HALF_UP);
        }
        return cashPercentage.multiply(HUNDRED).divide(limit.getThresholdPct(), 6, RoundingMode.HALF_UP);
    }

    private RiskBand bandFor(BigDecimal utilization) {
        if (utilization.compareTo(HIGH_THRESHOLD) >= 0) {
            return RiskBand.HIGH;
        }
        if (utilization.compareTo(MEDIUM_THRESHOLD) >= 0) {
            return RiskBand.MEDIUM;
        }
        return RiskBand.LOW;
    }
}