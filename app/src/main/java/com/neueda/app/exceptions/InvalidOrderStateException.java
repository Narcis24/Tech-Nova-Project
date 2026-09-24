package com.neueda.app.exceptions;

/**
 * Exception thrown when an order is asked to make a state transition it does not allow.
 */
public class InvalidOrderStateException extends TradingException {

    public InvalidOrderStateException(String message) {
        super(message);
    }

    public InvalidOrderStateException(String message, Throwable cause) {
        super(message, cause);
    }

}
