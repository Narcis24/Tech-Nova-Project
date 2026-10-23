package com.neueda.app.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Column;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.NoArgsConstructor;
import lombok.Getter;

/** One daily bar from price_data. The close is the price we trade and value at; the rest feeds the trade page chart and stats. */
@Entity
@Table(name = "price_data")
@IdClass(PriceKey.class)
@NoArgsConstructor
@Getter
public class Price {
    @Id
    @Column(name = "symbol")
    private String symbol;

    @Id
    @Column(name = "trade_date")
    private LocalDate tradeDate;

    @Column(name = "close")
    private BigDecimal price;

    @Column(name = "open")
    private BigDecimal open;

    @Column(name = "high")
    private BigDecimal high;

    @Column(name = "low")
    private BigDecimal low;

    /** Shares traded that day; null when the feed has none. */
    @Column(name = "volume")
    private Long volume;

    /** A bar known only by its close: open, high and low are the close too. */
    public Price(String symbol, LocalDate tradeDate, BigDecimal price) {
        this(symbol, tradeDate, price, price, price, price, null);
    }

    public Price(String symbol, LocalDate tradeDate, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal price,
                 Long volume) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null");
        }
        if (tradeDate == null) {
            throw new IllegalArgumentException("Trade date cannot be null");
        }
        if (price == null) {
            throw new IllegalArgumentException("Price cannot be null");
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0");
        }

        this.symbol = symbol;
        this.tradeDate = tradeDate;
        this.price = price;
        this.open = open;
        this.high = high;
        this.low = low;
        this.volume = volume;
    }
}
