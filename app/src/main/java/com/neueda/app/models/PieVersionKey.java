package com.neueda.app.models;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PieVersionKey implements Serializable {
    private UUID pieId;
    private int version;

    public PieVersionKey(UUID pieId, int version) {
        this.pieId = pieId;
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PieVersionKey that = (PieVersionKey) o;
        return version == that.version && Objects.equals(pieId, that.pieId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pieId, version);
    }
}
