package com.neueda.app.dtos;

import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.RiskBand;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminAccountResponse {
    // Always set, so the OpenAPI spec marks them required and generated clients don't type them as optional
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String accountId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String holderName;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal cashBalance;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private AccountStatus status;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private RiskBand riskBand;
}