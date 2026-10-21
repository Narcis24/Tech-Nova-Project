package com.neueda.app.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The target weight of one instrument in one pie version. */
@Entity
@Table(name = "pie_slices")
@IdClass(PieSliceKey.class)
@NoArgsConstructor
@Getter
public class PieSlice {
    @Id
    @Column(name = "pie_id")
    private UUID pieId;

    @Id
    @Column(name = "version")
    private int version;

    @Id
    @Column(name = "symbol")
    private String symbol;

    @Column(name = "weight_pct")
    private BigDecimal weightPct;

    public PieSlice(UUID pieId, int version, String symbol, BigDecimal weightPct) {
        this.pieId = pieId;
        this.version = version;
        this.symbol = symbol;
        this.weightPct = weightPct;
    }
}
