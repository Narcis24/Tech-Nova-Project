package com.neueda.app.exceptions;

/**
 * Exception thrown when a limit order is asked to fill before the market has reached its limit.
 */
public class OrderNotTriggeredException extends TradingException {

    public OrderNotTriggeredException(String message) {
        super(message);
    }

    public OrderNotTriggeredException(String message, Throwable cause) {
        super(message, cause);
    }

}
