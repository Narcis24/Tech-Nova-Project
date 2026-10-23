package com.neueda.app.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * What an instrument is and how the company or fund behind it is doing, as Yahoo Finance reports it.
 * Written only by data-pipeline/fundamentals.py; the app reads it for the trade page. Ratios are
 * fractions (0.0031 is 0.31%), money is USD, and any figure Yahoo does not have is null.
 */
@Entity
@Table(name = "instrument_profiles")
@Immutable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentProfile {
    @Id
    @Column(name = "symbol")
    private String symbol;

    /** EQUITY for a company, ETF for a fund. */
    @Column(name = "quote_type")
    private String quoteType;

    @Column(name = "sector")
    private String sector;

    @Column(name = "industry")
    private String industry;

    /** A fund's category, e.g. Large Blend. */
    @Column(name = "category")
    private String category;

    @Column(name = "fund_family")
    private String fundFamily;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "website")
    private String website;

    @Column(name = "employees")
    private Integer employees;

    @Column(name = "market_cap")
    private BigDecimal marketCap;

    /** A fund's assets under management. */
    @Column(name = "total_assets")
    private BigDecimal totalAssets;

    @Column(name = "trailing_pe")
    private BigDecimal trailingPe;

    @Column(name = "forward_pe")
    private BigDecimal forwardPe;

    @Column(name = "eps_ttm")
    private BigDecimal epsTtm;

    @Column(name = "eps_forward")
    private BigDecimal epsForward;

    @Column(name = "dividend_yield")
    private BigDecimal dividendYield;

    @Column(name = "expense_ratio")
    private BigDecimal expenseRatio;

    @Column(name = "beta")
    private BigDecimal beta;

    @Column(name = "revenue_ttm")
    private BigDecimal revenueTtm;

    @Column(name = "gross_profit_ttm")
    private BigDecimal grossProfitTtm;

    @Column(name = "profit_margin")
    private BigDecimal profitMargin;

    @Column(name = "revenue_growth")
    private BigDecimal revenueGrowth;

    /** When the pipeline last fetched it, in UTC. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
