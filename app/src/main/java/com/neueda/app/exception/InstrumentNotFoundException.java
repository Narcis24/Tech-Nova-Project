package com.neueda.app.exceptions;

/**
 * Exception thrown when a symbol is not a known instrument.
 */
public class InstrumentNotFoundException extends TradingException {

    public InstrumentNotFoundException(String message) {
        super(message);
    }

    public InstrumentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

}