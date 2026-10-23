package com.neueda.app.exceptions;

/** Raised when a pie cannot be deleted because one of the caller's accounts follows it. */
public class PieInUseException extends RuntimeException {

    public PieInUseException(String message) {
        super(message);
    }
}
