package com.neueda.app.exceptions;

/**
 * Exception thrown when an admin creates a user whose username or email is already taken.
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
