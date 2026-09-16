package com.neueda.app.exceptions;

/**
 * Exception thrown when an idempotency key is reused.
 */
public class DuplicateOrderException extends TradingException {

    public DuplicateOrderException(String message) {
        super(message);
    }

    public DuplicateOrderException(String message, Throwable cause) {
        super(message, cause);
    }

}