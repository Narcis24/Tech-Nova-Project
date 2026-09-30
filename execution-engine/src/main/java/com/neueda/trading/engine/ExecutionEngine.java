package com.neueda.trading.engine;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.trading.events.EventEnvelope;
import com.neueda.trading.events.OrderPlacedEvent;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ExecutionEngine {

    private final ObjectMapper objectMapper;

    public ExecutionEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "trades",
        groupId = "execution-engine"
    )
    public void handleOrderPlaced(EventEnvelope<?> envelope) {

        /*
         * Ignore anything that isn't an ORDER_PLACED event.
         */
        if (!"ORDER_PLACED".equals(envelope.eventType())) {
            log.debug(
                "Ignoring event type: {}",
                envelope.eventType()
            );
            return;
        }

        /*
         * Convert the generic Kafka payload into
         * an OrderPlacedEvent.
         */
        OrderPlacedEvent event;

        try {
            event = objectMapper.convertValue(
                envelope.payload(),
                OrderPlacedEvent.class
            );
        } catch (Exception e) {
            log.error(
                "Failed to deserialize ORDER_PLACED payload: payload={}",
                envelope.payload(),
                e
            );

            throw e;
        }

        UUID orderId = event.getOrderId();

        /*
         * For now the execution engine ONLY proves that
         * it successfully received the order from Kafka.
         */
        log.info(
            "=============================================="
        );

        log.info(
            "EXECUTION ENGINE RECEIVED ORDER"
        );

        log.info(
            "orderId={}",
            orderId
        );

        log.info(
            "accountId={}",
            event.getAccountId()
        );

        log.info(
            "symbol={}",
            event.getSymbol()
        );

        log.info(
            "side={}",
            event.getSide()
        );

        log.info(
            "orderType={}",
            event.getOrderType()
        );

        log.info(
            "quantity={}",
            event.getQuantity()
        );

        log.info(
            "price={}",
            event.getPrice()
        );

        log.info(
            "=============================================="
        );
    }
}