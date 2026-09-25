package com.neueda.app.configs;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;


import com.neueda.app.dtos.ErrorResponse;
import com.neueda.app.exceptions.AccountNotActiveException;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.DuplicateOrderException;
import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.exceptions.InsufficientFundsException;
import com.neueda.app.exceptions.InsufficientHoldingsException;
import com.neueda.app.exceptions.TradingException;

/**
 * Global Exception Handler for the Trading Application.
 * 
 * This class is responsible for catching all exceptions thrown across the application
 * (from controllers, services, repositories, etc.) and converting them into consistent,
 * user-friendly error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handles AccountNotFoundException when an account with the specified ID is not found.
     * 
     * @param ex the AccountNotFoundException thrown from the service layer
     * @return ResponseEntity containing ErrorResponse with 404 status
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        ErrorResponse error = new ErrorResponse("ACCOUNT_NOT_FOUND", ex.getMessage(), 401);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    /**
     * Handles AccountNotActiveException when an account is not in ACTIVE status.
     * 
     * @param ex the AccountNotActiveException thrown from the service layer
     * @return ResponseEntity containing ErrorResponse with 400 status
     */
    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(AccountNotActiveException ex) {
        ErrorResponse error = new ErrorResponse("ACCOUNT_NOT_ACTIVE", ex.getMessage(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles DuplicateOrderException when an attempt is made to create a duplicate order
     * using the same idempotency key.
     * 
     * @param ex the DuplicateOrderException thrown from the service layer
     * @return ResponseEntity containing ErrorResponse with 409 status
     */
    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateOrder(DuplicateOrderException ex) {
        ErrorResponse error = new ErrorResponse("DUPLICATE_ORDER", ex.getMessage(), 409);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handles InstrumentNotFoundException when a trading instrument (symbol) is not found
     * or does not exist in the system.
     * 
     * @param ex the InstrumentNotFoundException thrown from the service layer
     * @return ResponseEntity containing ErrorResponse with 404 status
     */
    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstrumentNotFound(InstrumentNotFoundException ex) {
        ErrorResponse error = new ErrorResponse("INSTRUMENT_NOT_FOUND", ex.getMessage(), 404);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handles InsufficientFundsException when an account does not have enough cash/balance
     * to complete a BUY order at the specified price and quantity.
     * 
     * @param ex the InsufficientFundsException thrown from the service layer
     * @return ResponseEntity containing ErrorResponse with 400 status
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        ErrorResponse error = new ErrorResponse("INSUFFICIENT_FUNDS", ex.getMessage(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles InsufficientHoldingsException when an account does not have enough shares/quantity
     * of a specific instrument to complete a SELL order.
     * 
     * @param ex the InsufficientHoldingsException thrown from the service layer
     * @return ResponseEntity containing ErrorResponse with 400 status
     */
    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientHoldings(InsufficientHoldingsException ex) {
        ErrorResponse error = new ErrorResponse("INSUFFICIENT_HOLDINGS", ex.getMessage(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Handles generic TradingException and its subclasses that are not explicitly handled
     * by more specific handlers (AccountNotFoundException, InstrumentNotFoundException, etc.).
     * 
     * Note: This handler catches TradingException but NOT its subclasses that have their
     * own @ExceptionHandler methods, as more specific handlers take precedence.
     * 
     * @param ex the TradingException thrown from the service layer
     * @return ResponseEntity containing ErrorResponse with 400 status
     */
    @ExceptionHandler(TradingException.class)
    public ResponseEntity<ErrorResponse> handleTradingException(TradingException ex) {
        ErrorResponse error = new ErrorResponse("TRADING_ERROR", ex.getMessage(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Handles IllegalArgumentException when invalid arguments are passed to a method.
     * This typically occurs during request validation (e.g., invalid side values, negative quantities).
     *       
     * @param ex the IllegalArgumentException thrown from the service layer
     * @return ResponseEntity containing ErrorResponse with 400 status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse("INVALID_ARGUMENT", ex.getMessage(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles MethodArgumentNotValidException when request body validation fails.
     * This occurs when @Valid annotation detects validation errors (e.g., @NotNull, @Positive, etc.).
     * 
     * @param ex the MethodArgumentNotValidException thrown by Spring validation
     * @return ResponseEntity containing ErrorResponse with 400 status
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
        
        ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", message, 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Fallback/Catch-all handler for ANY exception not handled by more specific handlers.
     * This includes Java built-in exceptions like NullPointerException, 
     * IllegalArgumentException, ArrayIndexOutOfBoundsException, etc.
     * 
     * Returns HTTP 500 (INTERNAL_SERVER_ERROR) as these are unexpected/unrecoverable errors.
     * The error message is generic for security reasons (not exposing stack traces to clients).
     * 
     * Note: Exception.class must be the LAST @ExceptionHandler as it's the most generic.
     * Spring matches handlers from most specific to least specific.
     * 
     * @param ex any Exception thrown anywhere in the application
     * @return ResponseEntity containing generic ErrorResponse with 500 status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", 500);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}