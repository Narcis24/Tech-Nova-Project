package com.neueda.app.exceptions;

/**
 * Exception thrown when an account holds no position in the requested symbol.
 */
public class PositionNotFoundException extends TradingException {

    public PositionNotFoundException(String message) {
        super(message);
    }

    public PositionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

}
