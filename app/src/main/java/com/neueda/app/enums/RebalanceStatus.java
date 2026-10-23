package com.neueda.app.enums;

import java.util.List;

public enum RebalanceStatus {
    SELLING,
    BUYING,
    COMPLETED,
    PARTIAL,
    CANCELLED;

    public static final List<RebalanceStatus> ACTIVE = List.of(SELLING, BUYING);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
