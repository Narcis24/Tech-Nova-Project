package com.neueda.app.dto;

import java.math.BigDecimal;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.model.Account;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    private String accountId;
    private String holderName;
    private BigDecimal cashBalance;
    private AccountStatus status;
    
    public AccountResponse(Account account) {
        this.accountId = account.getAccountId();
        this.holderName = account.getHolderName();
        this.cashBalance = account.getCashBalance();
        this.status = account.getAccountStatus();
    }
}
