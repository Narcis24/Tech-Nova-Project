package com.neueda.app.models;

import com.neueda.app.enums.PieVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A target mix of instruments. The weights live in versioned slices. */
@Entity
@Table(name = "pies")
@NoArgsConstructor
@Getter
public class Pie {
    @Id
    @Column(name = "pie_id")
    private UUID pieId;

    @Column(name = "owner_username")
    private String ownerUsername;

    @Setter
    @Column(name = "name")
    private String name;

    @Setter
    @Column(name = "description")
    private String description;

    @Column(name = "visibility")
    @Enumerated(EnumType.STRING)
    private PieVisibility visibility;

    @Setter
    @Column(name = "latest_version")
    private int latestVersion;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Setter
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** Goes public with a fresh publish time, which is also when its index starts. */
    public void publish(LocalDateTime now) {
        visibility = PieVisibility.PUBLIC;
        publishedAt = now;
    }

    /** Public and not deleted: the only state in which others can see and copy it. */
    public boolean isPublished() {
        return visibility == PieVisibility.PUBLIC && deletedAt == null;
    }

    public void unpublish() {
        visibility = PieVisibility.PRIVATE;
        publishedAt = null;
    }

    public static Pie create(String owner, String name, String description) {
        Pie pie = new Pie();
        pie.pieId = UUID.randomUUID();
        pie.ownerUsername = owner;
        pie.name = name;
        pie.description = description;
        pie.visibility = PieVisibility.PRIVATE;
        pie.latestVersion = 1;
        pie.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        return pie;
    }
}
