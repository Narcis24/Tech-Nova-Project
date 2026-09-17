package com.neueda.app.model;

import com.neueda.app.enums.AccountStatus;
import com.neueda.app.model.Account;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import java.time.LocalDateTime;

import java.math.BigDecimal;


public class AccountTest {
    private Account account;

    @BeforeEach 
    void setUp() {
        account = new Account(
            "12345", 
            "Karl Devon", 
            new BigDecimal(2000.00), 
            AccountStatus.ACTIVE,
            LocalDateTime.of(2026,9,17,13,00)
        );
    }

    @Test 
    void validateAccountGetters() {
        assertEquals(account.getAccountId(), "12345");
        assertEquals(account.getHolderName(), "Karl Devon");
        assertEquals(account.getCashBalance(), new BigDecimal(2000.00));
        assertEquals(account.getAccountStatus(), AccountStatus.ACTIVE);
        assertEquals(account.getLastUpdated(), LocalDateTime.of(2026,9,17,13,00));
    }

    
}
