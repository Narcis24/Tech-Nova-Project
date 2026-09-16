package com.neueda.app.exceptions;

/**
 * Exception thrown when a requested instrument (symbol) cannot be found.
 * This typically occurs when trying to place an order for a non-existent security.
 */
public class InstrumentNotFoundException extends TradingException {

    private final String symbol;

    /**
     * Constructs a new InstrumentNotFoundException for the specified symbol.
     *
     * @param symbol the symbol of the instrument that was not found
     */
    public InstrumentNotFoundException(String symbol) {
        super(String.format("Instrument with symbol '%s' not found", symbol));
        this.symbol = symbol;
    }

    /**
     * Constructs a new InstrumentNotFoundException with a custom detail message.
     *
     * @param message the detail message
     */
    public InstrumentNotFoundException(String message) {
        super(message);
        this.symbol = null;
    }

    /**
     * Constructs a new InstrumentNotFoundException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public InstrumentNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.symbol = null;
    }

    /**
     * Gets the symbol of the instrument.
     *
     * @return the symbol
     */
    public String getSymbol() {
        return symbol;
    }
}
