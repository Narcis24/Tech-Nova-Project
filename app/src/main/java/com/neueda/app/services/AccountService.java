package com.neueda.app.services;

import com.neueda.app.dtos.AccountResponse;
import com.neueda.app.exceptions.*;
import com.neueda.app.models.Account;
import com.neueda.app.repositories.AccountRepository;
import java.math.BigDecimal;
import java.util.UUID;

public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
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
    
}

