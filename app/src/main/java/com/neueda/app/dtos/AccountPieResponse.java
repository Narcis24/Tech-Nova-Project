package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The pie an account follows, with how far each slice has drifted from its target. A copy of a pie that is no
 * longer public keeps its applied version: latestVersion is then that version, and updateAvailable is false.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountPieResponse {
    private String accountId;
    private UUID pieId;
    private String pieName;
    private String mode;
    private Integer appliedVersion;
    private int latestVersion;
    private List<SliceDrift> drift;
    private BigDecimal maxDriftPct;
    /** The pie is public and not deleted. */
    private boolean published;
    /** A copy of a published pie whose latest version has not been applied. Also true until a first run applies one. */
    private boolean updateAvailable;
    /** The account's active rebalance run, if any. */
    private UUID activeRunId;
}
