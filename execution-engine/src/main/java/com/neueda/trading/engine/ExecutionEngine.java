package com.neueda.trading.engine;

import java.math.BigDecimal;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
        topics = "trades",
        groupId = "execution-engine"
    )
    public void handleOrderPlaced(EventEnvelope<?> envelope) {

        if (!"ORDER_PLACED".equals(envelope.eventType())) {
            return;
        }

        OrderPlacedEvent event = objectMapper.convertValue(
            envelope.payload(),
            OrderPlacedEvent.class
        );

        log.info(
            "Received ORDER_PLACED: orderId={}, symbol={}, quantity={}",
            event.getOrderId(),
            event.getSymbol(),
            event.getQuantity()
        );

        /*
         * TEMPORARY execution logic.
         *
         * LIMIT  -> supplied limit price
         * MARKET -> fixed test price
         *
         * Replace this later with a real market-price source.
         */
        BigDecimal executionPrice =
            event.getPrice() != null
                ? event.getPrice()
                : new BigDecimal("100.00");

        BigDecimal totalValue =
            executionPrice.multiply(
                BigDecimal.valueOf(event.getQuantity())
            );

        OrderExecutedEvent executedEvent =
            new OrderExecutedEvent(
                event.getOrderId(),
                UUID.fromString(event.getAccountId()),
                event.getSymbol(),
                event.getSide(),
                event.getQuantity(),
                executionPrice,
                totalValue
            );

        eventProducerService.publishEvent(
            "tradeEvents",
            event.getOrderId().toString(),
            "ORDER_EXECUTED",
            "ExecutionEngine",
            executedEvent
        );

        log.info(
            "Published ORDER_EXECUTED: orderId={}, executionPrice={}",
            event.getOrderId(),
            executionPrice
        );
    }
}