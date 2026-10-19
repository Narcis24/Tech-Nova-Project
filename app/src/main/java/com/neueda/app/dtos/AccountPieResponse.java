package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The pie an account follows, with how far each slice has drifted from its target. */
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
}
