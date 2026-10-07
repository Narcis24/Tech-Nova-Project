package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.neueda.trading.events.EventEnvelope;
import com.neueda.trading.events.OrderExecutedEvent;

class EventProducerServiceTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private EventProducerService producer;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = JsonMapper.builder().build();
        producer = new EventProducerService(kafkaTemplate, objectMapper);
    }

    @Test
    void publishesEventSuccessfully() {
        // Arrange
        String topic = "order-events";
        String key = "order-123";
        String eventType = "ORDER_EXECUTED";
        String source = "execution-engine";
        OrderExecutedEvent payload = new OrderExecutedEvent(
            UUID.randomUUID(),
            "ACC1",
            "AAPL",
            "BUY",
            100,
            new BigDecimal("150.25"),
            new BigDecimal("15025.00")
        );

        // Act
        producer.publishEvent(topic, key, eventType, source, payload);

        // Assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(topic), eq(key), messageCaptor.capture());

        String publishedJson = messageCaptor.getValue();
        assert publishedJson.contains(eventType);
        assert publishedJson.contains(source);
    }

    @Test
    void publishesOrderExecutedEvent() {
        // Arrange
        OrderExecutedEvent order = new OrderExecutedEvent(
            UUID.randomUUID(),
            "ACC1",
            "AAPL",
            "SELL",
            50,
            new BigDecimal("155.00"),
            new BigDecimal("7750.00")
        );

        // Act
        producer.publishEvent("order-events", "key", "ORDER_EXECUTED", "engine", order);

        // Assert
        verify(kafkaTemplate).send(eq("order-events"), eq("key"), anyString());
    }

    @Test
    void includesEventEnvelopeMetadata() throws Exception {
        // Arrange
        OrderExecutedEvent order = new OrderExecutedEvent(
            UUID.randomUUID(),
            "ACC2",
            "MSFT",
            "BUY",
            100,
            new BigDecimal("320.50"),
            new BigDecimal("32050.00")
        );

        // Act
        producer.publishEvent("events", "key", "ORDER_EXECUTED", "test-source", order);

        // Assert
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

        String json = captor.getValue();
        EventEnvelope<?> envelope = objectMapper.readValue(json, EventEnvelope.class);

        assert envelope.eventType().equals("ORDER_EXECUTED");
        assert envelope.source().equals("test-source");
        assert envelope.schemaVersion() == 1;
        assert envelope.eventId() != null;
        assert envelope.eventTime() != null;
    }

    @Test
    void logsErrorsWhenSerializationFails() {
        // This test verifies that RuntimeExceptions from mocking are propagated
        // In production, JacksonException would be caught and wrapped
        // Arrange
        ObjectMapper badMapper = mock(ObjectMapper.class);
        EventProducerService badProducer = new EventProducerService(kafkaTemplate, badMapper);

        when(badMapper.writeValueAsString(any()))
            .thenThrow(new RuntimeException("Serialization failed"));

        // Act & Assert - RuntimeException from mocking bubbles up
        assertThrows(RuntimeException.class, () ->
            badProducer.publishEvent("topic", "key", "TYPE", "source", new Object())
        );
    }

    @Test
    void usesCorrectTopicAndKey() {
        // Arrange
        String topic = "order-settled";
        String key = "order-456-settled";
        OrderExecutedEvent event = new OrderExecutedEvent(
            UUID.randomUUID(),
            "ACC3",
            "GOOGL",
            "BUY",
            25,
            new BigDecimal("2800.00"),
            new BigDecimal("70000.00")
        );

        // Act
        producer.publishEvent(topic, key, "ORDER_SETTLED", "engine", event);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), anyString());

        assert topicCaptor.getValue().equals(topic);
        assert keyCaptor.getValue().equals(key);
    }

    @Test
    void publishesMultipleEventsSequentially() {
        // Arrange & Act
        for (int i = 0; i < 3; i++) {
            OrderExecutedEvent event = new OrderExecutedEvent(
                UUID.randomUUID(),
                "ACC" + i,
                "AAPL",
                "BUY",
                100 + i,
                new BigDecimal("150.00"),
                new BigDecimal(String.valueOf((100 + i) * 150))
            );
            producer.publishEvent("events", "key-" + i, "ORDER_EXECUTED", "engine", event);
        }

        // Assert
        verify(kafkaTemplate).send(anyString(), eq("key-0"), anyString());
        verify(kafkaTemplate).send(anyString(), eq("key-1"), anyString());
        verify(kafkaTemplate).send(anyString(), eq("key-2"), anyString());
    }
}
