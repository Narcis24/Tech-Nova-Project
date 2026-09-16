package com.neueda.app.exceptions;

/**
 * Base exception class for all trading application exceptions.
 * This is the parent class for all custom exceptions in the system.
 * It extends RuntimeException to provide unchecked exception behavior.
 */
public class TradingException extends RuntimeException {

    /**
     * Constructs a new TradingException with the specified detail message.
     *
     * @param message the detail message
     */
    public TradingException(String message) {
        super(message);
    }

    /**
     * Constructs a new TradingException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public TradingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new TradingException with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public TradingException(Throwable cause) {
        super(cause);
    }
}
