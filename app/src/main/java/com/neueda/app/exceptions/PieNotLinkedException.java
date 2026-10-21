package com.neueda.app.exceptions;

/** Raised when an account does not follow a pie. */
public class PieNotLinkedException extends RuntimeException {

    public PieNotLinkedException(String message) {
        super(message);
    }
}
