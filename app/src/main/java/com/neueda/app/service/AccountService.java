package com.neueda.app.service;

import com.neueda.app.dto.AccountResponse;
import com.neueda.app.exception.*;
import com.neueda.app.model.Account;
import com.neueda.app.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.UUID;

public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountResponse getAccount(UUID accountId) {
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
    
}

