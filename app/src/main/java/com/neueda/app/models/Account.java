package com.neueda.app.models;

import com.neueda.app.enums.AccountStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.neueda.app.exceptions.AccountNotActiveException;
import com.neueda.app.exceptions.InsufficientFundsException;
import com.neueda.app.contracts.AccountOperations;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import lombok.NoArgsConstructor;
import lombok.Getter;

@Entity
@Table(name = "accounts")
@NoArgsConstructor
@Getter
public class Account  implements AccountOperations {

    @Id
    private String accountId;
    private String firstName;
    private String lastName;
    private BigDecimal cashBalance;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus;
    
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Position> positions = new ArrayList<>();

    public Account(String accountId, String holderName, BigDecimal cashBalance, AccountStatus accountStatus, LocalDateTime lastUpdated) {
        
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }

        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First Name cannot be null");
        }

        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Last Name cannot be null");
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
        this.firstName = firstName;
        this.lastName = lastName;
        this.cashBalance = cashBalance;
        this.accountStatus = accountStatus;
        this.lastUpdated = lastUpdated;
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
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", cashBalance=" + cashBalance +
                ", accountStatus=" + accountStatus +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}