package com.neueda.auth.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;


import com.neueda.auth.dto.ErrorResponse;
import com.neueda.auth.exception.InvalidCredentialsException;
import com.neueda.auth.exception.UsernameAlreadyExistException;

@RestControllerAdvice
public class GlobalExecutionHandler {
    
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials (InvalidCredentialsException ex) {
        ErrorResponse error = new ErrorResponse("INVALID_CREDENTIALS", ex.getMessage(), 401);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(UsernameAlreadyExistException.class)
    public ResponseEntity<ErrorResponse> handleExistingUsername (UsernameAlreadyExistException ex) {
        ErrorResponse error = new ErrorResponse("USERNAME_ALREADY_EXIST", ex.getMessage(), 401);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }


}
