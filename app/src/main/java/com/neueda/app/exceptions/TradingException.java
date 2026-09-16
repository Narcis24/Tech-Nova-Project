package com.neueda.app.exceptions;

/**
 * Base exception class for all trading application exceptions.
 * This is the parent class for all custom exceptions in the system.
 * It extends RuntimeException to provide unchecked exception behavior.
 */
public class TradingException extends RuntimeException {

    public TradingException(String message) {
        super(message);
    }

    public TradingException(String message, Throwable cause) {
        super(message, cause);
    }


}