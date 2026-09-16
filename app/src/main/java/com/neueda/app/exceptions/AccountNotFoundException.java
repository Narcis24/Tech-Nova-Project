package com.neueda.app.exceptions;

/**
 * Exception thrown when an account with the specified ID cannot be found.
 * This typically occurs when trying to retrieve or operate on an account that doesn't exist.
 */
public class AccountNotFoundException extends TradingException {

    /**
     * Constructs a new AccountNotFoundException with the specified account ID.
     *
     * @param accountId the ID of the account that was not found
     */
    public AccountNotFoundException(Long accountId) {
        super(String.format("Account with ID %d not found", accountId));
    }

    /**
     * Constructs a new AccountNotFoundException with a custom detail message.
     *
     * @param message the detail message
     */
    public AccountNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new AccountNotFoundException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
