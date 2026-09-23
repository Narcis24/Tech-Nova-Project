package com.neueda.app.service;

import com.neueda.app.enums.AccountStatus;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.model.Account;
import com.neueda.app.repository.AccountRepository;
import com.neueda.app.repository.OrderRepository;
import com.neueda.app.repository.PositionRepository;
import com.neueda.app.repository.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    private AccountRepository accountRepository;
    private OrderRepository orderRepository;
    private PositionRepository positionRepository;
    private PriceRepository priceRepository;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        orderRepository = mock(OrderRepository.class);
        positionRepository = mock(PositionRepository.class);
        priceRepository = mock(PriceRepository.class);
        accountService = new AccountService(accountRepository, orderRepository, positionRepository, priceRepository);
    }

    @Test
    void testGetAccountCashBalanceSuccess() {
        // Arrange
        String accountId = "12345";
        BigDecimal expectedBalance = new BigDecimal("10000.00");
        Account account = new Account(
            accountId,
            "John Doe",
            expectedBalance,
            AccountStatus.ACTIVE,
            LocalDateTime.of(2026, 9, 17, 13, 0)
        );

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));

        // Act
        BigDecimal result = accountService.getAccountCashBalance(accountId);

        // Assert
        assertEquals(expectedBalance, result);
        verify(accountRepository, times(1)).findById(accountId);
    }

    @Test
    void testGetAccountCashBalanceNotFound() {
        // Arrange
        String accountId = "UNKNOWN";
        when(accountRepository.findById(accountId))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            accountService.getAccountCashBalance(accountId);
        });
        verify(accountRepository, times(1)).findById(accountId);
    }

    @Test
    void testGetAccountCashBalanceZeroBalance() {
        // Arrange
        String accountId = "67890";
        Account account = new Account(
            accountId,
            "Jane Smith",
            BigDecimal.ZERO,
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));

        // Act
        BigDecimal result = accountService.getAccountCashBalance(accountId);

        // Assert
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void testGetAccountCashBalanceMultipleAccounts() {
        // Arrange
        Account account1 = new Account(
            "ACC001",
            "User 1",
            new BigDecimal("5000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );
        Account account2 = new Account(
            "ACC002",
            "User 2",
            new BigDecimal("15000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findById("ACC001"))
            .thenReturn(Optional.of(account1));
        when(accountRepository.findById("ACC002"))
            .thenReturn(Optional.of(account2));

        // Act & Assert
        assertEquals(new BigDecimal("5000.00"), accountService.getAccountCashBalance("ACC001"));
        assertEquals(new BigDecimal("15000.00"), accountService.getAccountCashBalance("ACC002"));
    }
}
