package com.neueda.app.exceptions;

/** Raised when a rebalance cannot start or change because of what the account is doing right now. */
public class RebalanceConflictException extends RuntimeException {

    public RebalanceConflictException(String message) {
        super(message);
    }
}
