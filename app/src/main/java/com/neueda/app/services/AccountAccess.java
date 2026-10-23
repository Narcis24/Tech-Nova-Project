package com.neueda.app.services;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.neueda.app.exceptions.AccountAccessDeniedException;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.OrderNotFoundException;
import com.neueda.app.exceptions.RebalanceRunNotFoundException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Order;
import com.neueda.app.models.RebalanceRun;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.RebalanceRunRepository;

/**
 * Accounts belong to the user who opened them. Controllers call this before touching an account
 * or an order: 404 if it does not exist, 403 if the caller (from the JWT) does not own it.
 */
@Component
public class AccountAccess {

    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final RebalanceRunRepository rebalanceRunRepository;

    public AccountAccess(AccountRepository accountRepository, OrderRepository orderRepository,
                         RebalanceRunRepository rebalanceRunRepository) {
        this.accountRepository = accountRepository;
        this.orderRepository = orderRepository;
        this.rebalanceRunRepository = rebalanceRunRepository;
    }

    public void requireOwned(String accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        if (!account.isOwnedBy(currentUser())) {
            throw new AccountAccessDeniedException("Account " + accountId + " does not belong to you");
        }
    }

    public void requireOwnedOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        requireOwned(order.getAccountId());
    }

    public void requireOwnedRun(UUID runId) {
        RebalanceRun run = rebalanceRunRepository.findById(runId)
            .orElseThrow(() -> new RebalanceRunNotFoundException("Rebalance run not found: " + runId));
        requireOwned(run.getAccountId());
    }

    /** The username JwtFilter put in the security context. */
    public String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AccountAccessDeniedException("No authenticated user");
        }
        return auth.getName();
    }
}
