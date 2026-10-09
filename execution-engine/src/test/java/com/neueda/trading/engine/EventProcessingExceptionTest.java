package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class EventProcessingExceptionTest {

    @Test
    void createsExceptionWithMessageAndCause() {
        String message = "Test error message";
        Exception cause = new RuntimeException("Root cause");
        
        EventProcessingException exception = new EventProcessingException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void createsExceptionWithNullCause() {
        String message = "Test error message";
        
        EventProcessingException exception = new EventProcessingException(message, null);

        assertEquals(message, exception.getMessage());
        assertEquals(null, exception.getCause());
    }

    @Test
    void isRuntimeException() {
        EventProcessingException exception = new EventProcessingException("test", null);
        assertNotNull(exception);
        assert exception instanceof RuntimeException;
    }

    @Test
    void preservesCauseStackTrace() {
        Exception cause = new Exception("Original cause");
        EventProcessingException exception = new EventProcessingException("Wrapper", cause);

        assertEquals(cause, exception.getCause());
        assertNotNull(exception.getCause().getStackTrace());
    }

    @Test
    void canBeThrowAndCaught() {
        EventProcessingException thrown = null;
        try {
            throw new EventProcessingException("Test", new RuntimeException("Cause"));
        } catch (EventProcessingException e) {
            thrown = e;
        }

        assertNotNull(thrown);
        assertEquals("Test", thrown.getMessage());
    }
}
