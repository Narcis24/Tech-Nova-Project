package com.neueda.app.exceptions;

/**
 * Exception thrown when there is no price data for a symbol.
 */
public class PriceNotFoundException extends TradingException {

    public PriceNotFoundException(String message) {
        super(message);
    }

    public PriceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

}
