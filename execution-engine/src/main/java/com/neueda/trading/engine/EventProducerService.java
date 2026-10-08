package com.neueda.trading.engine;

import java.time.Instant;
import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.neueda.events.EventEnvelope;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j //Creates a logger for you
public class EventProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventProducerService(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {

        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishEvent(
            String topic,
            String key,
            String eventType,
            String source,
            Object payload) {

        EventEnvelope<Object> envelope =
            new EventEnvelope<>(
                UUID.randomUUID().toString(),   // <-- FIXED
                eventType,
                Instant.now(),
                source,
                1,
                payload
            );

        try {

            // Convert the envelope object to JSON
            String json =
                objectMapper.writeValueAsString(envelope);

            log.info(
                "Publishing event: type={}, topic={}, key={}",
                eventType,
                topic,
                key
            );

            // Kafka uses StringSerializer,
            // so send the JSON String.
            kafkaTemplate.send(
                topic,
                key,
                json
            );

            log.info(
                "Published event: type={}, topic={}, key={}",
                eventType,
                topic,
                key
            );

        } catch (JacksonException e) {

            log.error(
                "Failed to publish event: type={}, topic={}, key={}",
                eventType,
                topic,
                key,
                e
            );

            throw new EventProcessingException(
                "Failed to publish Kafka event",
                e
            );
        }
    }
}