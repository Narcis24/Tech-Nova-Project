package com.neueda.app.models;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PieSliceKey implements Serializable {
    private UUID pieId;
    private int version;
    private String symbol;

    public PieSliceKey(UUID pieId, int version, String symbol) {
        this.pieId = pieId;
        this.version = version;
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PieSliceKey that = (PieSliceKey) o;
        return version == that.version && Objects.equals(pieId, that.pieId) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pieId, version, symbol);
    }
}
