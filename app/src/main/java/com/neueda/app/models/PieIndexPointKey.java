package com.neueda.app.models;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PieIndexPointKey implements Serializable {
    private UUID pieId;
    private LocalDate day;

    public PieIndexPointKey(UUID pieId, LocalDate day) {
        this.pieId = pieId;
        this.day = day;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PieIndexPointKey that = (PieIndexPointKey) o;
        return Objects.equals(pieId, that.pieId) && Objects.equals(day, that.day);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pieId, day);
    }
}
