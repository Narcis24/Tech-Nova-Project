package com.neueda.app.dtos;

import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.RiskBand;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminAccountResponse {
    private String accountId;
    private String holderName;
    private BigDecimal cashBalance;
    private AccountStatus status;
    private RiskBand riskBand;
}