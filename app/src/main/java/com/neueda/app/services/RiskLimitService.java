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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The risk limits that apply to an account: the default for each rule, replaced by the
 * holder's own threshold where they set one. The one place this is worked out, so the
 * limits a holder sees are the limits RiskService checks.
 */
@Service
public class RiskLimitService {

    private final RiskLimitRepository riskLimitRepository;
    private final AccountRiskLimitRepository accountRiskLimitRepository;

    public RiskLimitService(RiskLimitRepository riskLimitRepository, AccountRiskLimitRepository accountRiskLimitRepository) {
        this.riskLimitRepository = riskLimitRepository;
        this.accountRiskLimitRepository = accountRiskLimitRepository;
    }

    /** Every rule for the account, switched-off ones included. */
    public List<RiskLimitResponse> getLimits(String accountId) {
        Map<RiskRule, AccountRiskLimit> own = new EnumMap<>(RiskRule.class);
        for (AccountRiskLimit limit : accountRiskLimitRepository.findByAccountId(accountId)) {
            own.put(limit.getRule(), limit);
        }

        List<RiskLimitResponse> limits = new ArrayList<>();
        for (RiskLimit defaultLimit : riskLimitRepository.findAll()) {
            limits.add(toResponse(defaultLimit, own.get(defaultLimit.getRule())));
        }
        return limits;
    }

    /** Saves the holder's setting for one rule. */
    public RiskLimitResponse updateLimit(String accountId, RiskRule rule, UpdateRiskLimitRequest request) {
        RiskLimit defaultLimit = getDefault(rule);
        boolean enabled = request.getEnabled() == null || request.getEnabled();
        AccountRiskLimit custom = accountRiskLimitRepository.save(
            new AccountRiskLimit(accountId, rule, request.getThresholdPct(), enabled));
        return toResponse(defaultLimit, custom);
    }

    /** Drops the holder's setting for one rule, so the default applies again. */
    public RiskLimitResponse resetLimit(String accountId, RiskRule rule) {
        RiskLimit defaultLimit = getDefault(rule);
        accountRiskLimitRepository.deleteById(new AccountRiskLimitKey(accountId, rule));
        return toResponse(defaultLimit, null);
    }

    private RiskLimit getDefault(RiskRule rule) {
        return riskLimitRepository.findById(rule)
            .orElseThrow(() -> new IllegalStateException("No default risk limit for rule: " + rule));
    }

    private RiskLimitResponse toResponse(RiskLimit defaultLimit, AccountRiskLimit custom) {
        if (custom == null) {
            return new RiskLimitResponse(defaultLimit.getRule(), defaultLimit.getThresholdPct(),
                defaultLimit.getDirection(), true, LimitSource.DEFAULT);
        }
        if (custom.getThresholdPct() == null) {
            return new RiskLimitResponse(defaultLimit.getRule(), defaultLimit.getThresholdPct(),
                defaultLimit.getDirection(), custom.isEnabled(), LimitSource.DEFAULT);
        }
        return new RiskLimitResponse(defaultLimit.getRule(), custom.getThresholdPct(),
            defaultLimit.getDirection(), custom.isEnabled(), LimitSource.CUSTOM);
    }
}
