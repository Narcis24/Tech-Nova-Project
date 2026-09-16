package com.neueda.app.dto;

import com.neueda.app.enums.AccountStatus;
import lombok.Data;

@Data
public class AccountResponse {
    
    private Long accountID; 
    private double cash_balance;
    private AccountStatus status;
    
}
