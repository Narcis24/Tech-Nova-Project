package com.neueda.app.services;

import com.neueda.events.EventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class EventProducerService {

    private final KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate;

    public EventProducerService(KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes the event. Inside a transaction the send is deferred until it commits, so a
     * consumer never sees an event for rows that are not committed yet, and a rolled-back
     * change is never announced. Outside a transaction it is sent straight away.
     */
    public <T> void publishEvent(String topic, String key, String eventType, String source, T payload) {
        EventEnvelope<T> event = new EventEnvelope<>(
            UUID.randomUUID().toString(),  // eventId
            eventType,
            Instant.now(),
            source,
            1,  // schemaVersion
            payload
        );

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(topic, key, event);
                }
            });
        } else {
            send(topic, key, event);
        }
    }

    private void send(String topic, String key, EventEnvelope<?> event) {
        kafkaTemplate.send(topic, key, event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Published event: type={}, topic={}, key={}", event.eventType(), topic, key);
                } else {
                    log.error("Failed to publish event: type={}, topic={}, key={}", event.eventType(), topic, key, ex);
                }
            });
    }
}
