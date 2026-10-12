package com.neueda.app.dtos;

import com.neueda.app.enums.LimitSource;
import com.neueda.app.enums.RiskRule;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RiskWarning {
    private RiskRule rule;
    private String subject;          // symbol, asset class, or "CASH"
    private BigDecimal resultingPct;
    private BigDecimal limitPct;
    private LimitSource limitSource;
    private String message;
}
