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
import java.math.BigDecimal;


public class AccountTest {
    private Account account;
    private Account invalidAccount;

    @BeforeEach 
    void setUp() {
        account = new Account(
            "12345", 
            "Karl Devon", 
            new BigDecimal("2000.00"), 
            AccountStatus.ACTIVE,
            LocalDateTime.of(2026,9,17,13,00)
        );

                    
        invalidAccount = new Account(
            "123456", 
            "Debbie Lynch", 
            new BigDecimal("2000.00"), 
            AccountStatus.INACTIVE,
            LocalDateTime.of(2026,9,17,13,00)
            );
    }

    @Test 
    void validateAccountGetters() {
        assertAll(
            () -> assertEquals(account.getAccountId(), "12345"),
            () -> assertEquals(account.getHolderName(), "Karl Devon"),
            () -> assertEquals(account.getCashBalance(), new BigDecimal("2000.00")),
            () -> assertEquals(account.getAccountStatus(), AccountStatus.ACTIVE),
            () -> assertEquals(account.getLastUpdated(), LocalDateTime.of(2026,9,17,13,00))
        );
    }

    @Nested
    @DisplayName("Account Status Validation Checks")
    public class AccountStatusTests {

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
            Account inactiveAccount = new Account(
                "12345", 
                "Karl Devon", 
                new BigDecimal(2000.00), 
                AccountStatus.INACTIVE,
                LocalDateTime.of(2026,9,17,13,00)
            );
            
            AccountNotActiveException exception = assertThrows(
                AccountNotActiveException.class,
                () -> inactiveAccount.validateStatus());
                
                System.out.println(exception.getMessage());
            
                assertTrue(exception.getMessage().contains("12345"));
                assertTrue(exception.getMessage().contains("INACTIVE"));

        }

        @Test 
        void shouldReturnCorrectString() {
            Account testAccount = new Account(
                "12345", 
                "Karl Devon", 
                new BigDecimal(2000.00), 
                AccountStatus.ACTIVE,
                LocalDateTime.of(2026,9,17,13,00)
            );
            String result = testAccount.toString();

            assertEquals(result, "Account{accountId='12345', holderName='Karl Devon', cashBalance=2000, accountStatus=ACTIVE, lastUpdated=2026-09-17T13:00}");
        }
    }

    @Nested 
    @DisplayName ("Account Cash Balance Validation Checks")
    class CreditAndDebitTest {

        @Test
        void testDebitEntireBalance() {

            account.debitCash(new BigDecimal("2000.00"));

            assertEquals(
                    new BigDecimal("0.00"),
                    account.getCashBalance()
            );
        }

        @Test
        void testDebitCashWithInsufficientFunds() {
            InsufficientFundsException exception = assertThrows(
                InsufficientFundsException.class,
                () -> account.debitCash(new BigDecimal("3500.00"))
            );

            assertTrue(exception.getMessage().contains("12345"));
            assertTrue(exception.getMessage().contains("2000.00"));
            assertTrue(exception.getMessage().contains("3500.00"));
        }

        @Test
        void testBalanceDoesNotChangeWhenDebitFails() {

            assertThrows(InsufficientFundsException.class,
                    () -> account.debitCash(new BigDecimal("2500.00")));

            assertEquals(new BigDecimal("2000.00"), account.getCashBalance());
        }

        @Test
        void testLastUpdatedChangesAfterDebit() {

            LocalDateTime before = LocalDateTime.now();
            
            account.debitCash(new BigDecimal("100.00"));

            LocalDateTime after = LocalDateTime.now();

            assertTrue(!account.getLastUpdated().isBefore(before));
            assertTrue(!account.getLastUpdated().isAfter(after));
        }



    // // =========================
    // // creditCash()
    // // =========================

    // @Test
    // void testCreditCash() {

    //     account.creditCash(new BigDecimal("250.00"));

    //     assertEquals(
    //             new BigDecimal("1250.00"),
    //             account.getCashBalance()
    //     );
    // }

    // @Test
    // void testCreditCashWithSmallAmount() {

    //     account.creditCash(new BigDecimal("0.01"));

    //     assertEquals(
    //             new BigDecimal("1000.01"),
    //             account.getCashBalance()
    //     );
    // }

    // @Test
    // void testCreditCashWithZero() {

    //     IllegalArgumentException exception = assertThrows(
    //             IllegalArgumentException.class,
    //             () -> account.creditCash(BigDecimal.ZERO)
    //     );

    //     System.out.println(exception.getMessage());

    //     assertTrue(
    //             exception.getMessage().contains("Credit amount must be positive")
    //     );
    // }

    // @Test
    // void testCreditCashWithNegativeAmount() {

    //     IllegalArgumentException exception = assertThrows(
    //             IllegalArgumentException.class,
    //             () -> account.creditCash(new BigDecimal("-100.00"))
    //     );

    //     System.out.println(exception.getMessage());

    //     assertTrue(
    //             exception.getMessage().contains("Credit amount must be positive")
    //     );
    // }

    // @Test
    // void testBalanceDoesNotChangeWhenCreditFails() {

    //     assertThrows(
    //             IllegalArgumentException.class,
    //             () -> account.creditCash(new BigDecimal("-100.00"))
    //     );

    //     assertEquals(
    //             new BigDecimal("1000.00"),
    //             account.getCashBalance()
    //     );
    // }

    // @Test
    // void testLastUpdatedChangesAfterCredit() {

    //     LocalDateTime before = LocalDateTime.now();

    //     account.creditCash(new BigDecimal("100.00"));

    //     LocalDateTime after = LocalDateTime.now();

    //     assertTrue(
    //             !account.getLastUpdated().isBefore(before)
    //     );

    //     assertTrue(
    //             !account.getLastUpdated().isAfter(after)
    //     );
    // }
    }
}
