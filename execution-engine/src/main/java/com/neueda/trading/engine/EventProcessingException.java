package com.neueda.trading.engine;

/** A Kafka message could not be read or an event could not be published. Unchecked so the listener's error handler retries it. */
public class EventProcessingException extends RuntimeException {

    public EventProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
