package com.neueda.app.dto;

import java.math.BigDecimal;

import com.neueda.app.enums.AccountStatus;
import lombok.Data;

@Data
public class AccountResponse {
    
    private Long accountID; 
    private BigDecimal cash_balance;
    private AccountStatus status;
    
}
