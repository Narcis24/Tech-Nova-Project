package com.neueda.app.models;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PriceKey implements Serializable {
    private String symbol;
    private LocalDate tradeDate;

    public PriceKey(String symbol, LocalDate tradeDate) {
        this.symbol = symbol;
        this.tradeDate = tradeDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PriceKey that = (PriceKey) o;
        return Objects.equals(symbol, that.symbol) && Objects.equals(tradeDate, that.tradeDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, tradeDate);
    }
}
