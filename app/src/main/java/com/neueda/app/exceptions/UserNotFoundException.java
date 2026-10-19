package com.neueda.app.exceptions;

/**
 * Exception thrown when an admin acts on a username that does not exist.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
