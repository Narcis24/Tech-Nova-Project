package com.neueda.app.exceptions;

/**
 * Exception thrown when an account with the specified ID does not exist.
 */
public class AccountNotFoundException extends TradingException {

    public AccountNotFoundException(String message) {
        super(message);
    }
    
    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

 
}