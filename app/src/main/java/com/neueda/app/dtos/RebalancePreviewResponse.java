package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The trades that would bring an account to its pie's target weights. Nothing is placed. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RebalancePreviewResponse {
    private String accountId;
    private UUID pieId;
    private int version;
    private BigDecimal totalValue;
    private BigDecimal cashBefore;
    private BigDecimal cashAfter;
    private BigDecimal cashTargetPct;
    private List<PreviewLeg> legs;
    private List<RiskWarning> warnings;
}
