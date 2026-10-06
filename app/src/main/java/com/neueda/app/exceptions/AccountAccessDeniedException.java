package com.neueda.app.exceptions;

/**
 * Exception thrown when the caller asks for an account, or an order on an account, they do not own.
 * Not a TradingException, so it maps to 403 rather than the generic 400.
 */
public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException(String message) {
        super(message);
    }
}
