package com.neueda.app.dtos;

import java.math.BigDecimal;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.models.Account;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    // Always set (non-null in the entity), so the OpenAPI spec marks them required and generated clients don't type them as optional
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private String accountId;
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private String holderName;
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private BigDecimal cashBalance;
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private AccountStatus status;
    
    public AccountResponse(Account account) {
        this.accountId = account.getAccountId();
        this.holderName = account.getHolderName();
        this.cashBalance = account.getCashBalance();
        this.status = account.getAccountStatus();
    }
}
