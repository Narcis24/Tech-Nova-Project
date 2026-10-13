package com.neueda.app.dtos;

import com.neueda.app.enums.LimitSource;
import com.neueda.app.enums.RiskRule;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One rule as it applies to an account: the threshold in force and where it comes from. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RiskLimitResponse {
    private RiskRule rule;
    private BigDecimal thresholdPct;
    private String direction;        // ABOVE or BELOW
    private boolean enabled;
    private LimitSource source;
}
