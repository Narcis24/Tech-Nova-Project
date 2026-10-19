package com.neueda.app.models;

import com.neueda.app.enums.PieLinkMode;
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
import org.hibernate.annotations.DynamicUpdate;

/** The pie an account follows. An account follows at most one. */
@Entity
@DynamicUpdate
@Table(name = "account_pies")
@NoArgsConstructor
@Getter
public class AccountPie {
    @Id
    @Column(name = "account_id")
    private String accountId;

    @Column(name = "pie_id")
    private UUID pieId;

    @Column(name = "mode")
    @Enumerated(EnumType.STRING)
    private PieLinkMode mode;

    @Column(name = "applied_version")
    private Integer appliedVersion;

    @Setter
    @Column(name = "auto_sync")
    private boolean autoSync;

    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    public void applyVersion(int version) {
        this.appliedVersion = version;
    }

    /**
     * The version this link should be brought to: the pie's latest, except for a copy of a pie that is no longer
     * public, which stays at what it last applied. Null if that is nothing yet.
     */
    public Integer targetVersion(Pie pie) {
        return mode == PieLinkMode.COPY && !pie.isPublished() ? appliedVersion : Integer.valueOf(pie.getLatestVersion());
    }

    public boolean isBehind(Pie pie) {
        return appliedVersion == null || appliedVersion < pie.getLatestVersion();
    }

    /** Keeps the holdings but follows a pie of the user's own from now on, one already at {@code appliedVersion}. */
    public void makeOwn(UUID pieId, int appliedVersion) {
        this.pieId = pieId;
        this.mode = PieLinkMode.OWN;
        this.appliedVersion = appliedVersion;
        this.autoSync = false;
        this.linkedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public static AccountPie copy(String accountId, UUID pieId, boolean autoSync) {
        AccountPie link = own(accountId, pieId);
        link.mode = PieLinkMode.COPY;
        link.autoSync = autoSync;
        return link;
    }

    public static AccountPie own(String accountId, UUID pieId) {
        AccountPie link = new AccountPie();
        link.accountId = accountId;
        link.pieId = pieId;
        link.mode = PieLinkMode.OWN;
        link.linkedAt = LocalDateTime.now(ZoneOffset.UTC);
        return link;
    }
}
