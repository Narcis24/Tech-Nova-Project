package com.neueda.app.model;

import com.neueda.app.enums.*;
import com.neueda.app.model.*;
import com.neueda.app.exceptions.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;


import java.time.LocalDateTime;

import javax.security.auth.login.AccountNotFoundException;

import java.math.BigDecimal;


public class AccountTest {
    private Account account;
    private Account invalidAccount;

    @BeforeEach 
    void setUp() {
        account = new Account(
            "12345", 
            "Karl Devon", 
            new BigDecimal(2000.00), 
            AccountStatus.ACTIVE,
            LocalDateTime.of(2026,9,17,13,00)
        );

                    
        invalidAccount = new Account(
            "123456", 
            "Debbie Lynch", 
            new BigDecimal(2000.00), 
            AccountStatus.INACTIVE,
            LocalDateTime.of(2026,9,17,13,00)
            );
    }

    @Test 
    void validateAccountGetters() {
        assertAll(
            () -> assertEquals(account.getAccountId(), "12345"),
            () -> assertEquals(account.getHolderName(), "Karl Devon"),
            () -> assertEquals(account.getCashBalance(), new BigDecimal(2000.00)),
            () -> assertEquals(account.getAccountStatus(), AccountStatus.ACTIVE),
            () -> assertEquals(account.getLastUpdated(), LocalDateTime.of(2026,9,17,13,00))
        );
    }

    @Nested
    @DisplayName("Account Status Validation Checks")
    class AccountStatusTests {

        private Account testValidateAccountStatus;

        @ParameterizedTest
        @DisplayName("Allow Active Accounts")
        @EnumSource(AccountStatus.class)
        void validateForEachStatus(AccountStatus status) {
            testValidateAccountStatus = new Account(
                "12345", 
                "Karl Devon", 
                new BigDecimal(2000.00), 
                status,
                LocalDateTime.of(2026,9,17,13,00)
            );

            if (status == AccountStatus.ACTIVE) {
                assertDoesNotThrow(() -> testValidateAccountStatus.validateStatus());
            } else {
                assertThrows(AccountNotActiveException.class, () -> testValidateAccountStatus.validateStatus());
            }
        }
        
        @Test
        void errorMessageInExceptionMessage() {
            AccountNotActiveException exception = assertThrows(
                AccountNotActiveException.class,
                () -> testValidateAccountStatus.validateStatus());
                
                System.out.println(exception.getMessage());
            
                assertTrue(exception.getMessage().contains("12345"));
                assertTrue(exception.getMessage().contains("INACTIVE"));

        }

        @Test 
        void shouldReturnCorrectString() {
            String result = acocunt.toString();

            assertEquals(result.equals("Account{accountId='12345', holderName='Karl Devon', cashBAlance=2000.00, accountStatus=ACTIVE, lastUpdated=2026-09-17T10:30}"));
        }
    }
}
