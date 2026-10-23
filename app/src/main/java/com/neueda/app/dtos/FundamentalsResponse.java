package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * An instrument's profile, valuation and recent results for the trade page. Ratios are fractions
 * (0.0031 is 0.31%), money is USD; a figure the data source does not have is null. Funds have no
 * periods; companies list them oldest first, so charts read left to right.
 */
public record FundamentalsResponse(
    String symbol,
    String quoteType,
    String sector,
    String industry,
    String category,
    String fundFamily,
    String description,
    String website,
    Integer employees,
    BigDecimal marketCap,
    BigDecimal totalAssets,
    BigDecimal trailingPe,
    BigDecimal forwardPe,
    BigDecimal epsTtm,
    BigDecimal epsForward,
    BigDecimal dividendYield,
    BigDecimal expenseRatio,
    BigDecimal beta,
    BigDecimal revenueTtm,
    BigDecimal grossProfitTtm,
    BigDecimal profitMargin,
    BigDecimal revenueGrowth,
    Instant updatedAt,
    List<FinancialPeriod> quarters,
    List<FinancialPeriod> years
) {
    /** One reported quarter or year. */
    public record FinancialPeriod(
        LocalDate periodEnd,
        BigDecimal revenue,
        BigDecimal grossProfit,
        BigDecimal operatingIncome,
        BigDecimal netIncome,
        BigDecimal epsDiluted
    ) {}
}
