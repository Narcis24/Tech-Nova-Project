package com.neueda.app.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class EngineStartedListenerTest {

    private OrderService orderService;
    private EngineStartedListener listener;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        listener = new EngineStartedListener(orderService, JsonMapper.builder().build());
    }

    private static String envelope(String eventType) {
        return """
            {"eventId":"e1","eventType":"%s","eventTime":"2026-10-16T07:00:00Z",
             "source":"ExecutionEngine","schemaVersion":1,"payload":{}}""".formatted(eventType);
    }

    @Test
    void sendsPendingOrdersAgainWhenTheEngineStarts() {
        listener.onMessage(envelope("ENGINE_STARTED"));
        verify(orderService).republishPending();
    }

    @Test
    void ignoresFillsAndRejections() {
        listener.onMessage(envelope("ORDER_EXECUTED"));
        listener.onMessage(envelope("ORDER_REJECTED"));
        verify(orderService, never()).republishPending();
    }

    @Test
    void ignoresMessagesItCannotRead() {
        listener.onMessage("not json");
        verify(orderService, never()).republishPending();
    }
}
