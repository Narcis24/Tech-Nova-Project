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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String errorCode;           // e.g., "ACCOUNT_NOT_FOUND"
    private String message;              // e.g., "Account not found: ACC123"
    private int httpStatus;              // e.g., 404
    private LocalDateTime timestamp;     // When error occurred
    private String path;                 // e.g., "/v1/orders" (optional)
    private String traceId;   

    public ErrorResponse(String errorCode, String message, int httpStatus) {
        this.errorCode = errorCode;
        this.message = message;
        this.httpStatus = httpStatus;
        this.timestamp = LocalDateTime.now();
    }

    // Constructor with path
    public ErrorResponse(String errorCode, String message, int httpStatus, String path) {
        this(errorCode, message, httpStatus);
        this.path = path;
    }
}