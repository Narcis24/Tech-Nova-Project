package com.neueda.app.exceptions;

/**
 * Exception thrown when an account does not have sufficient holdings to complete a sell operation.
 */
public class InsufficientHoldingsException extends TradingException {

    public InsufficientHoldingsException(String message) {
        super(message);
    }
    public InsufficientHoldingsException(String message, Throwable cause) {
        super(message, cause);
    }


}