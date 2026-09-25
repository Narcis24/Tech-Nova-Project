package com.neueda.auth.exception;


/**
 * Exception thrown when login credentials are invalid
 */
public class InvalidLoginException extends RuntimeException {
    public InvalidLoginException(String message) {
        super(message);
    }

    public InvalidLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
