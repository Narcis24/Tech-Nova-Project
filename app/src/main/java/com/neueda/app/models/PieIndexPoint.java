package com.neueda.app.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The strategy index of a public pie on one UTC day. It is 100 on the publish day. */
@Entity
@Table(name = "pie_index_points")
@IdClass(PieIndexPointKey.class)
@NoArgsConstructor
@Getter
public class PieIndexPoint {
    @Id
    @Column(name = "pie_id")
    private UUID pieId;

    @Id
    @Column(name = "day")
    private LocalDate day;

    @Column(name = "value", precision = 18, scale = 6)
    private BigDecimal value;

    public PieIndexPoint(UUID pieId, LocalDate day, BigDecimal value) {
        this.pieId = pieId;
        this.day = day;
        this.value = value;
    }
}
