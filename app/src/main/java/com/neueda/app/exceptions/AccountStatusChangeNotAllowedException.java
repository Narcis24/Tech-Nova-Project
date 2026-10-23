package com.neueda.app.exceptions;

public class AccountStatusChangeNotAllowedException extends TradingException {
    public AccountStatusChangeNotAllowedException(String message) {
        super(message);
    }
}