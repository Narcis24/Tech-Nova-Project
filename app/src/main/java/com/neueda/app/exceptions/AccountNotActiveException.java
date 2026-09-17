package com.neueda.app.exceptions;

/**
 * Exception thrown when an account is suspended or closed.
 */
public class AccountNotActiveException extends TradingException {

    public AccountNotActiveException(String message) {
        super(message);
    }

    public AccountNotActiveException(String message, Throwable cause) {
        super(message, cause);
    }

  
}