package com.neueda.app.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.neueda.app.dtos.AccountResponse;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.exceptions.*;
import com.neueda.app.models.Account;
import com.neueda.app.models.Order;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final PositionValuator positionValuator;

    public AccountService(AccountRepository accountRepository, OrderRepository orderRepository, PositionRepository positionRepository, PositionValuator positionValuator) {
        this.accountRepository = accountRepository;
        this.orderRepository = orderRepository;
        this.positionRepository = positionRepository;
        this.positionValuator = positionValuator;
    }

    /** Opens an empty account for the user; they fund it with a deposit. */
    @Transactional
    public AccountResponse openAccount(String ownerUsername, String holderName) {
        String accountId = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new AccountResponse(accountRepository.save(Account.open(accountId, holderName, ownerUsername)));
    }

    public List<AccountResponse> getAccountsOwnedBy(String ownerUsername) {
        return accountRepository.findByOwnerUsernameOrderByAccountId(ownerUsername).stream()
            .map(AccountResponse::new)
            .toList();
    }

    public AccountResponse getAccount(String accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return new AccountResponse(account);
    }

    public BigDecimal getAccountCashBalance(String accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        return account.getCashBalance();
    }

    @Transactional
    public AccountResponse depositCash(String accountId, BigDecimal amount) {
        Account account = accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        account.validateStatus();  // Throws if not ACTIVE
        account.creditCash(amount);  // Validates amount > 0, updates balance + timestamp
        
        accountRepository.save(account);
        return new AccountResponse(account);
    }

    @Transactional
    public AccountResponse withdrawCash(String accountId, BigDecimal amount) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        account.validateStatus();  // Throws if not ACTIVE
        account.debitCash(amount);  // Throws if insufficient funds
        
        accountRepository.save(account);
        return new AccountResponse(account);
    }

    public List<OrderResponse> getAccountOrders(String accountId) {
        // Verify account exists
        accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        // Retrieve all orders for the account
        List<Order> orders = orderRepository.findByAccountId(accountId);
        
        // Convert to OrderResponse DTOs
        return orders.stream()
            .map(OrderResponse::new)
            .collect(Collectors.toList());
    }

    public List<PositionResponse> getAccountPositions(String accountId) {
        // Verify account exists
        accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        return positionValuator.valueAll(positionRepository.findByAccountId(accountId));
    }
}

