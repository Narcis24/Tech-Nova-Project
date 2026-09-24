package com.neueda.app.controllers;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.neueda.app.dtos.ErrorResponse;
import com.neueda.app.exceptions.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({AccountNotFoundException.class, InstrumentNotFoundException.class,
                       OrderNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(TradingException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({DuplicateOrderException.class, InvalidOrderStateException.class})
    public ResponseEntity<ErrorResponse> handleConflict(TradingException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Valid request that a business rule refuses: inactive account, no funds, no holdings, etc. */
    @ExceptionHandler(TradingException.class)
    public ResponseEntity<ErrorResponse> handleTradingException(TradingException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Another transaction changed the same row first; the client can retry. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "Concurrent update, please retry");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }
}
