package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.trading.events.EventEnvelope;
import com.neueda.trading.events.OrderPlacedEvent;
import com.neueda.trading.events.OrderExecutedEvent;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ExecutionEngine {

    private final ObjectMapper objectMapper;
    private final EventProducerService eventProducerService;

    public ExecutionEngine(
            ObjectMapper objectMapper,
            EventProducerService eventProducerService) {

        this.objectMapper = objectMapper;
        this.eventProducerService = eventProducerService;
    }

    @KafkaListener(
        topics = "order-request",
        groupId = "execution-engine"
    )
    public void handleOrderPlaced(String message) {

        log.info("Received message from order-request topic: {}", message);

        /*
         * Kafka is configured with StringDeserializer,
         * so the incoming Kafka value is JSON represented
         * as a String.
         *
         * Convert the JSON string into our EventEnvelope.
         */
        EventEnvelope<?> envelope;

        try {
            envelope = objectMapper.readValue(
                message,
                EventEnvelope.class
            );
        } catch (Exception e) {

            log.error(
                "Failed to deserialize Kafka message into EventEnvelope: {}",
                message,
                e
            );

            throw new RuntimeException(
                "Failed to deserialize EventEnvelope",
                e
            );
        }

        /*
         * This consumer only handles ORDER_PLACED events.
         */
        if (!"ORDER_PLACED".equals(envelope.eventType())) {

            log.debug(
                "Ignoring event type: {}",
                envelope.eventType()
            );

            return;
        }

        /*
         * EventEnvelope<?> means Jackson will normally deserialize
         * payload as a Map.
         *
         * Convert that Map into OrderPlacedEvent.
         */
        OrderPlacedEvent event;

        try {
            event = objectMapper.convertValue(
                envelope.payload(),
                OrderPlacedEvent.class
            );
        } catch (Exception e) {

            log.error(
                "Failed to convert payload into OrderPlacedEvent: {}",
                envelope.payload(),
                e
            );

            throw new RuntimeException(
                "Failed to deserialize OrderPlacedEvent",
                e
            );
        }

        log.info(
            "Received ORDER_PLACED: orderId={}, accountId={}, symbol={}, side={}, orderType={}, quantity={}, price={}",
            event.getOrderId(),
            event.getAccountId(),
            event.getSymbol(),
            event.getSide(),
            event.getOrderType(),
            event.getQuantity(),
            event.getPrice()
        );

        /*
         * TEMPORARY EXECUTION LOGIC
         *
         * LIMIT:
         * Use the supplied limit price.
         *
         * MARKET:
         * Use a fixed simulated price of 100.00.
         *
         * Later this can be replaced with a market-data service
         * or marketData Kafka topic.
         */
        BigDecimal executionPrice =
            event.getPrice() != null
                ? event.getPrice()
                : new BigDecimal("100.00");

        BigDecimal totalValue =
            executionPrice.multiply(
                BigDecimal.valueOf(event.getQuantity())
            );

        log.info(
            "Executing order: orderId={}, executionPrice={}, totalValue={}",
            event.getOrderId(),
            executionPrice,
            totalValue
        );

        /*
         * Build the result that will be sent back to app/.

         * unless OrderExecutedEvent specifically requires UUID
         * and your account IDs are actually UUIDs.
         */
        OrderExecutedEvent executedEvent =
            new OrderExecutedEvent(
                event.getOrderId(),
                event.getAccountId(),
                event.getSymbol(),
                event.getSide(),
                event.getQuantity(),
                executionPrice,
                totalValue
            );

        /*
         * Send result back to Kafka.
         *
         * app/ listens to order-execution and handles the
         * account/order/position database updates.
         */
        eventProducerService.publishEvent(
            "order-execution",
            event.getOrderId().toString(),
            "ORDER_EXECUTED",
            "ExecutionEngine",
            executedEvent
        );

        log.info(
            "Published ORDER_EXECUTED: orderId={}, executionPrice={}, totalValue={}",
            event.getOrderId(),
            executionPrice,
            totalValue
        );
    }
}