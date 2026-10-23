package com.neueda.app.exceptions;

/** Raised when a pie's name, slices or weights break the rules. */
public class InvalidPieException extends RuntimeException {

    public InvalidPieException(String message) {
        super(message);
    }
}
