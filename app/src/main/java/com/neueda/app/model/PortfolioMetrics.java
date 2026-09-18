package com.neueda.app.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PortfolioMetrics(
    BigDecimal totalMarketValue,
    BigDecimal totalCash,
    BigDecimal totalUnrealizedPnL
) {
    
    public BigDecimal getTotalPortfolioValue() {
        return totalMarketValue.add(totalCash);
    }
    
    public BigDecimal getPortfolioReturn() {
        BigDecimal totalValue = getTotalPortfolioValue();
        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalUnrealizedPnL.divide(totalValue, 4, RoundingMode.HALF_UP);
    }
}