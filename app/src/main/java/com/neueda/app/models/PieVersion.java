package com.neueda.app.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One immutable version of a pie. */
@Entity
@Table(name = "pie_versions")
@IdClass(PieVersionKey.class)
@NoArgsConstructor
@Getter
public class PieVersion {
    @Id
    @Column(name = "pie_id")
    private UUID pieId;

    @Id
    @Column(name = "version")
    private int version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public PieVersion(UUID pieId, int version, LocalDateTime createdAt) {
        this.pieId = pieId;
        this.version = version;
        this.createdAt = createdAt;
    }
}
