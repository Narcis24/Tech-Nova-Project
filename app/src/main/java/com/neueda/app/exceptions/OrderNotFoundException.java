package com.neueda.app.exceptions;

/**
 * Exception thrown when an order with the specified ID does not exist.
 */
public class OrderNotFoundException extends TradingException {

    public OrderNotFoundException(String message) {
        super(message);
    }

    public OrderNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

}
