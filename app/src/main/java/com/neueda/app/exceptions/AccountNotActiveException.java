package com.neueda.app.exceptions;

import com.neueda.app.enums.AccountStatus;

/**
 * Exception thrown when an operation is attempted on an account that is not active.
 * This includes operations on suspended, inactive, or closed accounts.
 */
public class AccountNotActiveException extends TradingException {

    private final Long accountId;
    private final AccountStatus accountStatus;

    /**
     * Constructs a new AccountNotActiveException for the specified account.
     *
     * @param accountId     the ID of the account
     * @param accountStatus the current status of the account
     */
    public AccountNotActiveException(Long accountId, AccountStatus accountStatus) {
        super(String.format("Account %d is not active. Current status: %s", accountId, accountStatus));
        this.accountId = accountId;
        this.accountStatus = accountStatus;
    }

    /**
     * Constructs a new AccountNotActiveException with a custom detail message.
     *
     * @param message the detail message
     */
    public AccountNotActiveException(String message) {
        super(message);
        this.accountId = null;
        this.accountStatus = null;
    }

    /**
     * Gets the account ID.
     *
     * @return the account ID
     */
    public Long getAccountId() {
        return accountId;
    }

    /**
     * Gets the account status.
     *
     * @return the account status
     */
    public AccountStatus getAccountStatus() {
        return accountStatus;
    }
}
