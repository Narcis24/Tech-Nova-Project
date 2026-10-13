package com.neueda.app.models;

import com.neueda.app.enums.*;
import com.neueda.app.exceptions.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;


class AccountTest {
    private Account account;

    @BeforeEach 
    void setUp() {
        account = new Account(
            "12345", 
            "Karl Devon", 
            new BigDecimal("2000.00"), 
            AccountStatus.ACTIVE,
            LocalDateTime.of(2026,9,17,13,00)
        );
    }

    @Test 
    void validateAccountGetters() {
        assertAll(
            () -> assertEquals("12345", account.getAccountId()),
            () -> assertEquals("Karl Devon", account.getHolderName()),
            () -> assertEquals(new BigDecimal("2000.00"), account.getCashBalance()),
            () -> assertEquals(AccountStatus.ACTIVE, account.getAccountStatus()),
            () -> assertEquals(LocalDateTime.of(2026,9,17,13,00), account.getLastUpdated())
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
            Account inactiveAccount = new Account(
                "12345", 
                "Karl Devon", 
                new BigDecimal(2000.00), 
                AccountStatus.INACTIVE,
                LocalDateTime.of(2026,9,17,13,00)
            );
            
            AccountNotActiveException exception = assertThrows(
                AccountNotActiveException.class,
                inactiveAccount::validateStatus);
            
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

            assertEquals("Account{accountId='12345', holderName='Karl Devon', cashBalance=2000, accountStatus=ACTIVE, lastUpdated=2026-09-17T13:00}", result);
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
            BigDecimal amount = new BigDecimal("3500.00");
            InsufficientFundsException exception = assertThrows(
                InsufficientFundsException.class,
                () -> account.debitCash(amount)
            );

            assertTrue(exception.getMessage().contains("12345"));
            assertTrue(exception.getMessage().contains("2000.00"));
            assertTrue(exception.getMessage().contains("3500.00"));
        }

        @Test
        void testBalanceDoesNotChangeWhenDebitFails() {

            BigDecimal amount = new BigDecimal("2500.00");
            assertThrows(InsufficientFundsException.class,
                    () -> account.debitCash(amount));

            assertEquals(new BigDecimal("2000.00"), account.getCashBalance());
        }

        @Test
        void testNegativeOrZeroDebitIsRejected() {
            BigDecimal negative = new BigDecimal("-100.00");
            assertThrows(IllegalArgumentException.class,
                    () -> account.debitCash(negative));
            assertThrows(IllegalArgumentException.class,
                    () -> account.debitCash(BigDecimal.ZERO));

            assertEquals(new BigDecimal("2000.00"), account.getCashBalance());
        }

        @Test
        void testLastUpdatedChangesAfterDebit() {

            LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
            
            account.debitCash(new BigDecimal("100.00"));

            LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

            assertTrue(!account.getLastUpdated().isBefore(before));
            assertTrue(!account.getLastUpdated().isAfter(after));
        }
    }
}
