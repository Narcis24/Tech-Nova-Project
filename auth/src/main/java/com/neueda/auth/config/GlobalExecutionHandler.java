package com.neueda.auth.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;


import com.neueda.app.dto.ErrorResponse;
import com.neueda.app.exception.InvalidCredentialsException;
import com.neueda.app.exception.UsernameAlreadyExistsException;

@RestControllerAdvice
public class GlobalExecutionHandler {
    
    @ExecutionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials (InvalidCredentialsException ex) {
        ErrorResponse error = new ErrorResponse("INVALID_CREDENTIALS", ex.getMessage(), 401);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExecutionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleExistingUsername (UsernameAlreadyExistsException ex) {
        ErrorResponse error = new ErrorResponse("USERNAME_ALREADY_EXIST", ex.getMessage(), 401);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }


}
