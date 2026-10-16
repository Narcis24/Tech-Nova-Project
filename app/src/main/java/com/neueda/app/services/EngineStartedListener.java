package com.neueda.app.services;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.neueda.events.EventEnvelope;
import com.neueda.events.EventTypes;
import com.neueda.events.Topics;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * The execution engine keeps waiting orders only in memory, so after a restart it announces
 * ENGINE_STARTED and this sends it every PENDING order again. Its own consumer group, so it reads
 * the engine's messages alongside settlement and ignores everything else.
 */
@Service
@Slf4j
public class EngineStartedListener {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public EngineStartedListener(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.ORDER_EXECUTION, groupId = "engine-restart")
    public void onMessage(String message) {
        EventEnvelope<?> envelope;
        try {
            envelope = objectMapper.readValue(message, EventEnvelope.class);
        } catch (Exception e) {
            // Settlement logs bad messages on this topic already
            return;
        }
        if (EventTypes.ENGINE_STARTED.equals(envelope.eventType())) {
            orderService.republishPending();
        }
    }
}
