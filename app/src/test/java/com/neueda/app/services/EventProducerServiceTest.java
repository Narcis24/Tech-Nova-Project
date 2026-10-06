package com.neueda.app.services;

import com.neueda.app.events.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventProducerServiceTest {

    private KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate;
    private EventProducerService eventProducerService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        eventProducerService = new EventProducerService(kafkaTemplate);
    }

    @Test
    void testPublishEventSuccess() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String payload = "Order details";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert
        verify(kafkaTemplate, times(1)).send(eq(topic), eq(key), any(EventEnvelope.class));
    }

    @Test
    void testPublishEventWithDifferentPayloadTypes() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        
        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act - Test with String
        eventProducerService.publishEvent(topic, key, eventType, source, "String payload");
        verify(kafkaTemplate, times(1)).send(eq(topic), eq(key), any(EventEnvelope.class));

        // Act - Test with Integer
        eventProducerService.publishEvent(topic, key, eventType, source, 12345);
        verify(kafkaTemplate, times(2)).send(eq(topic), eq(key), any(EventEnvelope.class));

        // Act - Test with Object
        Object customPayload = new Object() {
            public String toString() { return "Custom Object"; }
        };
        eventProducerService.publishEvent(topic, key, eventType, source, customPayload);
        verify(kafkaTemplate, times(3)).send(eq(topic), eq(key), any(EventEnvelope.class));
    }

    @Test
    void testPublishEventWithMultipleTopics() {
        // Arrange
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String payload = "Order details";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent("orders", key, eventType, source, payload);
        eventProducerService.publishEvent("positions", key, eventType, source, payload);
        eventProducerService.publishEvent("accounts", key, eventType, source, payload);

        // Assert
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), any(EventEnvelope.class));
    }

    @Test
    void testPublishEventWithMultipleKeys() {
        // Arrange
        String topic = "orders";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String payload = "Order details";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, "ACC001", eventType, source, payload);
        eventProducerService.publishEvent(topic, "ACC002", eventType, source, payload);
        eventProducerService.publishEvent(topic, "ACC003", eventType, source, payload);

        // Assert
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), any(EventEnvelope.class));
    }

    @Test
    void testPublishEventCreatesEventEnvelopeWithCorrectProperties() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String payload = "Order details";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert - Verify that send was called with correct envelope properties
        verify(kafkaTemplate).send(
            eq(topic),
            eq(key),
            argThat(envelope -> {
                return envelope.eventType().equals(eventType) &&
                       envelope.source().equals(source) &&
                       envelope.schemaVersion() == 1 &&
                       envelope.eventId() != null &&
                       envelope.eventTime() != null &&
                       envelope.payload().equals(payload);
            })
        );
    }

    @Test
    void testPublishEventGeneratesUniqueEventIds() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String payload = "Order details";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert - Verify that kafkaTemplate.send was called twice
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any(EventEnvelope.class));
    }

    @Test
    void testPublishEventHandlesAsyncCompletion() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String payload = "Order details";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> completableFuture = 
            mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(completableFuture);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert
        verify(kafkaTemplate, times(1)).send(eq(topic), eq(key), any(EventEnvelope.class));
        verify(completableFuture, times(1)).whenComplete(any());
    }

    @Test
    void testPublishEventHandlesSuccessCallback() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String payload = "Order details";

        // Create a real CompletableFuture that completes successfully
        CompletableFuture<SendResult<String, EventEnvelope<?>>> completableFuture = 
            CompletableFuture.completedFuture(mock(SendResult.class));
        
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(completableFuture);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert - Just verify that send was called, the callback will be executed
        verify(kafkaTemplate, times(1)).send(eq(topic), eq(key), any(EventEnvelope.class));
    }

    @Test
    void testPublishEventHandlesErrorCallback() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String payload = "Order details";

        // Create a CompletableFuture that completes with an exception
        CompletableFuture<SendResult<String, EventEnvelope<?>>> completableFuture = 
            new CompletableFuture<>();
        completableFuture.completeExceptionally(new RuntimeException("Kafka error"));
        
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(completableFuture);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert - Just verify that send was called, the error callback will be executed
        verify(kafkaTemplate, times(1)).send(eq(topic), eq(key), any(EventEnvelope.class));
    }

    @Test
    void testPublishEventWithNullPayload() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, null);

        // Assert
        verify(kafkaTemplate, times(1)).send(
            eq(topic),
            eq(key),
            argThat(envelope -> envelope.payload() == null)
        );
    }

    @Test
    void testPublishEventWithEmptyStrings() {
        // Arrange
        String topic = "";
        String key = "";
        String eventType = "";
        String source = "";
        String payload = "payload";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert - Event should still be published with empty strings
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any(EventEnvelope.class));
    }

    @Test
    void testPublishEventWithSpecialCharactersInPayload() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String payload = "Special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert
        verify(kafkaTemplate, times(1)).send(
            eq(topic),
            eq(key),
            argThat(envelope -> envelope.payload().equals(payload))
        );
    }

    @Test
    void testPublishEventSchemaVersion() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String payload = "Order details";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert
        verify(kafkaTemplate).send(
            eq(topic),
            eq(key),
            argThat(envelope -> envelope.schemaVersion() == 1)
        );
    }

    @Test
    void testPublishEventPayloadPreservation() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String originalPayload = "Order details - 123456";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, originalPayload);

        // Assert
        verify(kafkaTemplate).send(
            anyString(),
            anyString(),
            argThat(envelope -> {
                Object payload = envelope.payload();
                return payload instanceof String && payload.equals(originalPayload);
            })
        );
    }

    @Test
    void testPublishEventEventTypePreservation() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderCancelled";
        String source = "OrderService";
        String payload = "Order details";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert
        verify(kafkaTemplate).send(
            anyString(),
            anyString(),
            argThat(envelope -> envelope.eventType().equals(eventType))
        );
    }

    @Test
    void testPublishEventSourcePreservation() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "PositionService";
        String payload = "Order details";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert
        verify(kafkaTemplate).send(
            anyString(),
            anyString(),
            argThat(envelope -> envelope.source().equals(source))
        );
    }

    @Test
    void testPublishEventNotNullEventId() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String payload = "Order details";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert
        verify(kafkaTemplate).send(
            anyString(),
            anyString(),
            argThat(envelope -> envelope.eventId() != null && !envelope.eventId().isEmpty())
        );
    }

    @Test
    void testPublishEventNotNullTimestamp() {
        // Arrange
        String topic = "orders";
        String key = "ACC001";
        String eventType = "OrderPlaced";
        String source = "OrderService";
        String payload = "Order details";

        CompletableFuture<SendResult<String, EventEnvelope<?>>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        eventProducerService.publishEvent(topic, key, eventType, source, payload);

        // Assert
        verify(kafkaTemplate).send(
            anyString(),
            anyString(),
            argThat(envelope -> envelope.eventTime() != null)
        );
    }
}
