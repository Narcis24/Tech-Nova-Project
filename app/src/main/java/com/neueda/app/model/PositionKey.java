package com.neueda.app.model;

import java.io.Serializable;
import java.util.Objects;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PositionKey implements Serializable {
    private String accountId;
    private String symbol;

    public PositionKey(String accountId, String symbol) {
        this.accountId = accountId;
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PositionKey that = (PositionKey) o;
        return Objects.equals(accountId, that.accountId) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, symbol);
    }
}
