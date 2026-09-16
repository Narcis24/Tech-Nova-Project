package com.neueda.app.exceptions;

/**
 * Exception thrown when an account does not have sufficient holdings (shares) to complete a sell operation.
 * This occurs when attempting to sell more shares than currently held in a position.
 */
public class InsufficientHoldingsException extends TradingException {

    private final Long currentHoldings;
    private final Long requiredHoldings;
    private final String symbol;

    /**
     * Constructs a new InsufficientHoldingsException with current and required holdings.
     *
     * @param symbol             the instrument symbol
     * @param currentHoldings    the current quantity held
     * @param requiredHoldings   the quantity required to sell
     */
    public InsufficientHoldingsException(String symbol, Long currentHoldings, Long requiredHoldings) {
        super(String.format("Insufficient holdings for %s. Available: %d, Required: %d", 
                symbol, currentHoldings, requiredHoldings));
        this.symbol = symbol;
        this.currentHoldings = currentHoldings;
        this.requiredHoldings = requiredHoldings;
    }

    /**
     * Constructs a new InsufficientHoldingsException with a custom detail message.
     *
     * @param message the detail message
     */
    public InsufficientHoldingsException(String message) {
        super(message);
        this.symbol = null;
        this.currentHoldings = null;
        this.requiredHoldings = null;
    }

    /**
     * Constructs a new InsufficientHoldingsException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public InsufficientHoldingsException(String message, Throwable cause) {
        super(message, cause);
        this.symbol = null;
        this.currentHoldings = null;
        this.requiredHoldings = null;
    }

    /**
     * Gets the instrument symbol.
     *
     * @return the symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Gets the current holdings quantity.
     *
     * @return the current holdings
     */
    public Long getCurrentHoldings() {
        return currentHoldings;
    }

    /**
     * Gets the required holdings quantity.
     *
     * @return the required holdings
     */
    public Long getRequiredHoldings() {
        return requiredHoldings;
    }

    /**
     * Gets the shortfall amount.
     *
     * @return the shortfall (required - current), or null if amounts are not available
     */
    public Long getShortfall() {
        if (currentHoldings != null && requiredHoldings != null) {
            return requiredHoldings - currentHoldings;
        }
        return null;
    }
}
