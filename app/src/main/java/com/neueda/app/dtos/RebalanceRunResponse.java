package com.neueda.app.dtos;

import com.neueda.app.enums.RebalanceStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A rebalance run: SELLING, then BUYING, then COMPLETED or PARTIAL, or CANCELLED. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RebalanceRunResponse {
    private UUID runId;
    private String accountId;
    private UUID pieId;
    private int targetVersion;
    private RebalanceStatus status;
    /** The run is waiting on orders the market is closed for. */
    private boolean waitingForMarketOpen;
    private String failureSummary;
    private Instant createdAt;
    private Instant updatedAt;
    private List<RebalanceLegResponse> legs;
}
