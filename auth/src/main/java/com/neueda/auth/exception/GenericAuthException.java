package com.neueda.auth.exception;


/**
 * Auth Exception for any handling generic auth errors
 */
public class GenericAuthException extends RuntimeException {
    public GenericAuthException(String message) {
        super(message);
    }

    public GenericAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
