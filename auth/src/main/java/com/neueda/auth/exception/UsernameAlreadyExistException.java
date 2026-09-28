package com.neueda.auth.exception;


/**
 * Exception thrown when username already exist in registration
 */
public class UsernameAlreadyExistException extends RuntimeException {
    public UsernameAlreadyExistException(String message) {
        super(message);
    }

    public UsernameAlreadyExistException(String message, Throwable cause) {
        super(message, cause);
    }
}
