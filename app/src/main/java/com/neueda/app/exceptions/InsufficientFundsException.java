package com.neueda.app.exceptions;

/**
 * Exception thrown when an account does not have sufficient funds to complete an operation.
 */
public class InsufficientFundsException extends TradingException {

    public InsufficientFundsException(String message) {
        super(message);
    }
    
    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }

}