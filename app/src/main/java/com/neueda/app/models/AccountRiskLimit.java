package com.neueda.app.models;

import com.neueda.app.enums.RiskRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An account holder's own setting for one risk rule. A null threshold keeps the default. */
@Entity
@Table(name = "account_risk_limits")
@IdClass(AccountRiskLimitKey.class)
@NoArgsConstructor
@Getter
public class AccountRiskLimit {
    @Id
    @Column(name = "account_id")
    private String accountId;

    @Id
    @Column(name = "rule")
    @Enumerated(EnumType.STRING)
    private RiskRule rule;

    @Column(name = "threshold_pct")
    private BigDecimal thresholdPct;

    @Column(name = "enabled")
    private boolean enabled;

    public AccountRiskLimit(String accountId, RiskRule rule, BigDecimal thresholdPct, boolean enabled) {
        this.accountId = accountId;
        this.rule = rule;
        this.thresholdPct = thresholdPct;
        this.enabled = enabled;
    }
}
