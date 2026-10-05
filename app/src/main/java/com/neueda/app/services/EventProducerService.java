package com.neueda.app.services;

import com.neueda.app.events.EventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class EventProducerService {

    private final KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate;

    public EventProducerService(KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public <T> void publishEvent(String topic, String key, String eventType, String source, T payload) {
        EventEnvelope<T> event = new EventEnvelope<>(
            UUID.randomUUID().toString(),  // eventId
            eventType,
            Instant.now(),
            source,
            1,  // schemaVersion
            payload
        );

        kafkaTemplate.send(topic, key, event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Published event: type={}, topic={}, key={}", eventType, topic, key);
                } else {
                    log.error("Failed to publish event: type={}, topic={}, key={}", eventType, topic, key, ex);
                }
            });
    }
}