package com.neueda.app.exceptions;

/** Raised when a public pie's slices change twice within 24 hours. */
public class PieEditTooSoonException extends RuntimeException {

    public PieEditTooSoonException(String message) {
        super(message);
    }
}
