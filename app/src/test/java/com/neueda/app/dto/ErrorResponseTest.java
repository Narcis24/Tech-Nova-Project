package com.neueda.app.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ErrorResponseTest {
    
    @Test
    public void testErrorResponseCreation() {
        ErrorResponse response = new ErrorResponse();
        response.setError_code("ERR_001");
        response.setMessage("Invalid request");

        assertEquals("ERR_001", response.getError_code());
        assertEquals("Invalid request", response.getMessage());
    }
}
