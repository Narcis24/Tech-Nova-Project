package com.neueda.app.models;

import com.neueda.app.enums.RebalanceStatus;
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

/** One execution of "bring this account to version N" of a pie. The target version never changes. */
@Entity
@Table(name = "rebalance_runs")
@NoArgsConstructor
@Getter
public class RebalanceRun {
    private static final int MAX_SUMMARY = 1000;

    @Id
    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "pie_id")
    private UUID pieId;

    @Column(name = "target_version")
    private int targetVersion;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private RebalanceStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "failure_summary")
    private String failureSummary;

    public static RebalanceRun start(String accountId, UUID pieId, int targetVersion) {
        RebalanceRun run = new RebalanceRun();
        run.runId = UUID.randomUUID();
        run.accountId = accountId;
        run.pieId = pieId;
        run.targetVersion = targetVersion;
        run.status = RebalanceStatus.SELLING;
        run.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        run.updatedAt = run.createdAt;
        return run;
    }

    public boolean isActive() {
        return status.isActive();
    }

    public void startBuying() {
        status = RebalanceStatus.BUYING;
        touch();
    }

    /** Ends the run: COMPLETED if nothing failed, PARTIAL otherwise. */
    public void finish() {
        status = failureSummary == null ? RebalanceStatus.COMPLETED : RebalanceStatus.PARTIAL;
        touch();
    }

    public void cancel(String reason) {
        status = RebalanceStatus.CANCELLED;
        addFailure(reason);
        touch();
    }

    public void addFailure(String failure) {
        String joined = failureSummary == null ? failure : failureSummary + "; " + failure;
        failureSummary = joined.length() <= MAX_SUMMARY ? joined : joined.substring(0, MAX_SUMMARY - 1) + "…";
        touch();
    }

    private void touch() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
