package com.neueda.app.events;

import com.neueda.app.enums.RebalanceStatus;
import java.util.UUID;

/** A run reached a terminal status: COMPLETED, PARTIAL or CANCELLED. Published inside the transaction that ended it. */
public record RebalanceRunFinished(UUID runId, String accountId, UUID pieId, RebalanceStatus status) {}
