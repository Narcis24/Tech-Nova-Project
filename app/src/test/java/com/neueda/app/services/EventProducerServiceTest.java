package com.neueda.app.services;

import com.neueda.app.events.EventEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventProducerServiceTest {

    private KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate;
    private EventProducerService producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        producer = new EventProducerService(kafkaTemplate);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsImmediatelyOutsideATransaction() {
        producer.publishEvent("order-request", "k1", "ORDER_PLACED", "test", "payload");

        verify(kafkaTemplate).send(eq("order-request"), eq("k1"), any());
    }

    @Test
    void defersSendUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();

        producer.publishEvent("order-request", "k1", "ORDER_PLACED", "test", "payload");
        verifyNoInteractions(kafkaTemplate);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(kafkaTemplate).send(eq("order-request"), eq("k1"), any());
    }

    @Test
    void neverSendsOnRollback() {
        TransactionSynchronizationManager.initSynchronization();

        producer.publishEvent("order-request", "k1", "ORDER_PLACED", "test", "payload");
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(kafkaTemplate);
    }
}
