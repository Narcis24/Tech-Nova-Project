package com.neueda.app.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** A company's income statement lines for one reported quarter or year. Written by data-pipeline/fundamentals.py. */
@Entity
@Table(name = "instrument_financials")
@IdClass(InstrumentFinancial.Key.class)
@Immutable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentFinancial {

    public enum PeriodType { QUARTERLY, ANNUAL }

    @Id
    @Column(name = "symbol")
    private String symbol;

    @Id
    @Column(name = "period_type")
    @Enumerated(EnumType.STRING)
    private PeriodType periodType;

    @Id
    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "revenue")
    private BigDecimal revenue;

    @Column(name = "gross_profit")
    private BigDecimal grossProfit;

    @Column(name = "operating_income")
    private BigDecimal operatingIncome;

    @Column(name = "net_income")
    private BigDecimal netIncome;

    @Column(name = "eps_diluted")
    private BigDecimal epsDiluted;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private String symbol;
        private PeriodType periodType;
        private LocalDate periodEnd;
    }
}
