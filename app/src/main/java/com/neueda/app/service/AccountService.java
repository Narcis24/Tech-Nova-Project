package com.neueda.app.service;

import org.springframework.stereotype.Service;
import com.neueda.app.dto.AccountResponse;
import com.neueda.app.dto.PositionResponse;
import com.neueda.app.dto.OrderResponse;
import com.neueda.app.exceptions.*;
import com.neueda.app.model.Account;
import com.neueda.app.repository.AccountRepository;
import com.neueda.app.repository.PositionRepository;
import com.neueda.app.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;

    public AccountService(AccountRepository accountRepository, 
                        PositionRepository positionRepository,
                        OrderRepository orderRepository) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
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

    public AccountResponse depositCash(String accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        account.validateStatus();  // Throws if not ACTIVE
        account.creditCash(amount);  // Validates amount > 0, updates balance + timestamp
        
        accountRepository.update(account);
        return new AccountResponse(account);
    }

    public AccountResponse withdrawCash(String accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        account.validateStatus();  // Throws if not ACTIVE
        account.debitCash(amount);  // Throws if insufficient funds
        
        accountRepository.update(account);
        return new AccountResponse(account);
    }

    public PositionResponse getAccountPositions(String accountId) {
        // Verify account exists
        accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        
        // Get positions for this account
        return new PositionResponse(positionRepository.findByAccountId(accountId));
    }

    public OrderResponse getAccountOrders(String accountId) {
        // Verify account exists
        accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        
        // Get orders for this account
        return new OrderResponse(orderRepository.findByAccountId(accountId));
    }
    
}

