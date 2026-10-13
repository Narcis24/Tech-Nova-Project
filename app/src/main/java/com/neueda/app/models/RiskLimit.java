package com.neueda.app.models;

import com.neueda.app.enums.RiskRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** System-wide default for one risk rule. */
@Entity
@Table(name = "risk_limits")
@NoArgsConstructor
@Getter
public class RiskLimit {
    @Id
    @Column(name = "rule")
    @Enumerated(EnumType.STRING)
    private RiskRule rule;

    @Column(name = "threshold_pct")
    private BigDecimal thresholdPct;

    /** ABOVE: warn when exposure reaches the threshold. BELOW: warn when it falls to the threshold. */
    @Column(name = "direction")
    private String direction;

    public RiskLimit(RiskRule rule, BigDecimal thresholdPct, String direction) {
        this.rule = rule;
        this.thresholdPct = thresholdPct;
        this.direction = direction;
    }
}
