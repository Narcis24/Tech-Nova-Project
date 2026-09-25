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

/** One daily bar from price_data. Only the close is mapped, as that is the price we trade and value at. */
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

    public Price(String symbol, LocalDate tradeDate, BigDecimal price) {
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
    }
}
