package com.neueda.app.model;

import com.neueda.app.enums.AccountStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.neueda.app.exceptions.AccountNotActiveException;
import com.neueda.app.exceptions.InsufficientFundsException;
import com.neueda.app.contract.AccountOperations;

public class Account  implements AccountOperations {
    private String accountId;
    private String holderName;
    private BigDecimal cashBalance;
    private AccountStatus accountStatus;
    private LocalDateTime lastUpdated;

    public Account(String accountId, String holderName, BigDecimal cashBalance, AccountStatus accountStatus, LocalDateTime lastUpdated) {
        
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }

        if (holderName == null || holderName.isBlank()) {
            throw new IllegalArgumentException("Holder Name cannot be null");
        }

        if (cashBalance == null || cashBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cash balance must be a positive number");
        }

        if (accountStatus == null) {
            throw new IllegalArgumentException("Account Status cannot be null");
        }

        if (lastUpdated == null) {
            throw new IllegalArgumentException("Last updated cannot be null");
        }
        
        this.accountId = accountId;
        this.holderName = holderName;
        this.cashBalance = cashBalance;
        this.accountStatus = accountStatus;
        this.lastUpdated = lastUpdated;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getHolderName() {
        return holderName;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    /* This method will throw a custom exception if the account is not ACTIVE */
    public void validateStatus() {
        if (accountStatus != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                "Account " + accountId + " is " + accountStatus + ", not ACTIVE"
            );
        }
    }

    /* This method will throw a custom exception if the account has insufficient funds */
    public void debitCash(BigDecimal amount) {
        if (cashBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                "Account " + accountId + " has $" + cashBalance + 
                " but order requires $" + amount
            );
        }
        this.cashBalance = cashBalance.subtract(amount);
        this.lastUpdated = LocalDateTime.now();
    }

    
    public void creditCash(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.cashBalance = cashBalance.add(amount);
        this.lastUpdated = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Account{" +
                "accountId='" + accountId + '\'' +
                ", holderName='" + holderName + '\'' +
                ", cashBalance=" + cashBalance +
                ", accountStatus=" + accountStatus +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}