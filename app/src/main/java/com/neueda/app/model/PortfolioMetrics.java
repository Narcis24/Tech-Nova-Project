package com.neueda.app.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PortfolioMetrics(
    BigDecimal totalMarketValue,
    BigDecimal totalCash,
    BigDecimal totalUnrealizedPnL
) {
    public PortfolioMetrics {
        if (totalMarketValue == null) {
            throw new IllegalArgumentException("Total Market Value cannot be null");
        }
        if (totalCash == null) {
            throw new IllegalArgumentException("Total Cash Value cannot be null");
        }
        if (totalUnrealizedPnL == null) {
            throw new IllegalArgumentException("Total unrealized PnL Value cannot be null");
        }
        if (totalMarketValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total Market Value cannot be negative");
        }
        if (totalCash.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total Cash cannot be negative");
        }
    }
    
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