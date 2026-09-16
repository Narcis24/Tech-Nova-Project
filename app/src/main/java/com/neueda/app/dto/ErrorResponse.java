package com.neueda.app.dto;
import lombok.Data;

@Data
public class ErrorResponse {
    
    private String error_code;
    private String message;
}
