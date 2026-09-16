package com.neueda.app.exceptions;

import java.math.BigDecimal;

/**
 * Exception thrown when an account does not have sufficient funds to complete an operation.
 * This typically occurs when attempting to place an order that exceeds available cash balance.
 */
public class InsufficientFundsException extends TradingException {

    private final BigDecimal availableFunds;
    private final BigDecimal requiredFunds;

    /**
     * Constructs a new InsufficientFundsException with available and required funds.
     *
     * @param availableFunds the current available funds in the account
     * @param requiredFunds  the funds required for the operation
     */
    public InsufficientFundsException(BigDecimal availableFunds, BigDecimal requiredFunds) {
        super(String.format("Insufficient funds. Available: %s, Required: %s", availableFunds, requiredFunds));
        this.availableFunds = availableFunds;
        this.requiredFunds = requiredFunds;
    }

    /**
     * Constructs a new InsufficientFundsException with a custom detail message.
     *
     * @param message the detail message
     */
    public InsufficientFundsException(String message) {
        super(message);
        this.availableFunds = null;
        this.requiredFunds = null;
    }

    /**
     * Constructs a new InsufficientFundsException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
        this.availableFunds = null;
        this.requiredFunds = null;
    }

    /**
     * Gets the available funds.
     *
     * @return the available funds
     */
    public BigDecimal getAvailableFunds() {
        return availableFunds;
    }

    /**
     * Gets the required funds.
     *
     * @return the required funds
     */
    public BigDecimal getRequiredFunds() {
        return requiredFunds;
    }

    /**
     * Gets the shortfall amount.
     *
     * @return the shortfall (required - available), or null if amounts are not available
     */
    public BigDecimal getShortfall() {
        if (availableFunds != null && requiredFunds != null) {
            return requiredFunds.subtract(availableFunds);
        }
        return null;
    }
}
