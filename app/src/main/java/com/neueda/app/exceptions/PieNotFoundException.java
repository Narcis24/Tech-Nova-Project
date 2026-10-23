package com.neueda.app.exceptions;

/** Raised when a pie does not exist or is not owned by the caller. */
public class PieNotFoundException extends RuntimeException {

    public PieNotFoundException(String message) {
        super(message);
    }
}
