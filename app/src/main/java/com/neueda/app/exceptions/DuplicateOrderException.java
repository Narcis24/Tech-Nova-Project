package com.neueda.app.exceptions;

/**
 * Exception thrown when an order with a duplicate idempotency key is submitted.
 * This is used to prevent accidental submission of duplicate orders through idempotent operations.
 */
public class DuplicateOrderException extends TradingException {

    private final String idempotencyKey;
    private final Long existingOrderId;

    /**
     * Constructs a new DuplicateOrderException for the specified idempotency key.
     *
     * @param idempotencyKey   the idempotency key of the duplicate order
     * @param existingOrderId  the ID of the existing order with the same key
     */
    public DuplicateOrderException(String idempotencyKey, Long existingOrderId) {
        super(String.format("An order with idempotency key '%s' already exists (Order ID: %d)", 
                idempotencyKey, existingOrderId));
        this.idempotencyKey = idempotencyKey;
        this.existingOrderId = existingOrderId;
    }

    /**
     * Constructs a new DuplicateOrderException with a custom detail message.
     *
     * @param message the detail message
     */
    public DuplicateOrderException(String message) {
        super(message);
        this.idempotencyKey = null;
        this.existingOrderId = null;
    }

    /**
     * Constructs a new DuplicateOrderException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public DuplicateOrderException(String message, Throwable cause) {
        super(message, cause);
        this.idempotencyKey = null;
        this.existingOrderId = null;
    }

    /**
     * Gets the idempotency key.
     *
     * @return the idempotency key
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Gets the existing order ID.
     *
     * @return the existing order ID
     */
    public Long getExistingOrderId() {
        return existingOrderId;
    }
}
