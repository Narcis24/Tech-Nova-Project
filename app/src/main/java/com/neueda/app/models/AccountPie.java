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

/** The pie an account follows. An account follows at most one. */
@Entity
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

    @Column(name = "auto_sync")
    private boolean autoSync;

    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    public void applyVersion(int version) {
        this.appliedVersion = version;
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
