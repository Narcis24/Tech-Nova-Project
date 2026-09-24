package com.neueda.app.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String errorCode;           // e.g., "ACCOUNT_NOT_FOUND"
    private String message;              // e.g., "Account not found: ACC123"
    private int httpStatus;              // e.g., 404
    private LocalDateTime timestamp;     // When error occurred

    public ErrorResponse(String errorCode, String message, int httpStatus) {
        this.errorCode = errorCode;
        this.message = message;
        this.httpStatus = httpStatus;
        this.timestamp = LocalDateTime.now();
    }

}