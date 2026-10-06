package com.neueda.app.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.neueda.app.enums.AccountStatus;
import com.neueda.app.exceptions.AccountAccessDeniedException;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.OrderNotFoundException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Order;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.OrderRepository;

class AccountAccessTest {

    private AccountRepository accountRepository;
    private OrderRepository orderRepository;
    private AccountAccess accountAccess;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        orderRepository = mock(OrderRepository.class);
        accountAccess = new AccountAccess(accountRepository, orderRepository);
        loginAs("alice_user");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    @Test
    void ownerIsAllowed() {
        when(accountRepository.findById("ACC-1")).thenReturn(Optional.of(Account.open("ACC-1", "Alice", "alice_user")));

        assertDoesNotThrow(() -> accountAccess.requireOwned("ACC-1"));
    }

    @Test
    void otherUserIsDenied() {
        when(accountRepository.findById("ACC-1")).thenReturn(Optional.of(Account.open("ACC-1", "Bob", "bob_user")));

        assertThrows(AccountAccessDeniedException.class, () -> accountAccess.requireOwned("ACC-1"));
    }

    @Test
    void unclaimedSeedAccountIsDeniedToEveryone() {
        Account seed = new Account("ACC001", "Alice Johnson", new BigDecimal("50000.00"), AccountStatus.ACTIVE, LocalDateTime.now());
        when(accountRepository.findById("ACC001")).thenReturn(Optional.of(seed));

        assertThrows(AccountAccessDeniedException.class, () -> accountAccess.requireOwned("ACC001"));
    }

    @Test
    void missingAccountIsNotFound() {
        when(accountRepository.findById("NOPE")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountAccess.requireOwned("NOPE"));
    }

    @Test
    void orderIsCheckedThroughItsAccount() {
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);
        when(order.getAccountId()).thenReturn("ACC-1");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accountRepository.findById("ACC-1")).thenReturn(Optional.of(Account.open("ACC-1", "Bob", "bob_user")));

        assertThrows(AccountAccessDeniedException.class, () -> accountAccess.requireOwnedOrder(orderId));
    }

    @Test
    void missingOrderIsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> accountAccess.requireOwnedOrder(orderId));
    }

    @Test
    void noAuthenticatedUserIsDenied() {
        SecurityContextHolder.clearContext();

        assertThrows(AccountAccessDeniedException.class, () -> accountAccess.currentUser());
    }
}
