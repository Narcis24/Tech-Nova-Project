package com.neueda.app.exceptions;

import org.springframework.http.HttpStatus;

/** The admin journal page could not be served; carries the status and code the admin should see. */
public class JournalException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public JournalException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
