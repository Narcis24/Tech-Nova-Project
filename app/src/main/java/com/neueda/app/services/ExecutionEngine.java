package com.neueda.app.services;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.app.dtos.OrderExecutedEvent;
import com.neueda.app.dtos.OrderPlacedEvent;
import com.neueda.app.enums.OrderType;
import com.neueda.app.events.EventEnvelope;
import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.exceptions.OrderNotFoundException;
import com.neueda.app.models.Order;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class ExecutionEngine {

    private final OrderRepository orderRepository;
    private final InstrumentRepository instrumentRepository;
    private final PriceService priceService;
    private final EventProducerService eventProducerService;
    private final ObjectMapper objectMapper;

    public ExecutionEngine(
            OrderRepository orderRepository,
            InstrumentRepository instrumentRepository,
            PriceService priceService,
            EventProducerService eventProducerService,
            ObjectMapper objectMapper) {

        this.orderRepository = orderRepository;
        this.instrumentRepository = instrumentRepository;
        this.priceService = priceService;
        this.eventProducerService = eventProducerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "trades", groupId = "execution-engine")
    public void handleOrderPlaced(EventEnvelope<?> envelope) {

        /*
         * Kafka/Jackson may deserialize the generic EventEnvelope payload
         * as a LinkedHashMap instead of OrderPlacedEvent.
         *
         * Explicitly convert the payload to OrderPlacedEvent.
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
            return;
        }

        UUID orderId = event.getOrderId();

        log.info(
            "Received ORDER_PLACED event: orderId={}, symbol={}, orderType={}",
            orderId,
            event.getSymbol(),
            event.getOrderType()
        );

        try {

            // Find the order in the database
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                    new OrderNotFoundException(
                        "Order not found: " + orderId
                    )
                );

            /*
             * Get current market price.
             *
             * MARKET orders execute at this price.
             * LIMIT orders only execute if the market price
             * satisfies the limit condition.
             */
            BigDecimal marketPrice =
                priceService.getCurrentPrice(event.getSymbol());

            log.info(
                "Current market price retrieved: orderId={}, symbol={}, marketPrice={}",
                orderId,
                event.getSymbol(),
                marketPrice
            );

            // Check LIMIT order trigger
            if (order.getOrderType() == OrderType.LIMIT) {

                if (!order.isTriggeredBy(marketPrice)) {

                    log.info(
                        "LIMIT order not triggered: orderId={}, limit={}, marketPrice={}",
                        orderId,
                        event.getPrice(),
                        marketPrice
                    );

                    return;
                }
            }

            /*
             * The order can now execute.
             *
             * Execution price is the current market price.
             */
            BigDecimal executionPrice = marketPrice;

            BigDecimal totalValue = executionPrice.multiply(
                BigDecimal.valueOf(event.getQuantity())
            );

            log.info(
                "Order triggered for execution: orderId={}, executionPrice={}, totalValue={}",
                orderId,
                executionPrice,
                totalValue
            );

            /*
             * Create ORDER_EXECUTED event.
             *
             * ExecutionEngine does NOT update cash/positions.
             * OrderService will consume this event and perform
             * the database updates.
             */
            OrderExecutedEvent executedEvent =
                new OrderExecutedEvent(
                    orderId,
                    event.getAccountId(),
                    event.getSymbol(),
                    event.getSide(),
                    event.getQuantity(),
                    executionPrice,
                    totalValue
                );

            /*
             * Publish execution result.
             */
            eventProducerService.publishEvent(
                "tradeEvents",
                orderId.toString(),
                "ORDER_EXECUTED",
                "ExecutionEngine",
                executedEvent
            );

            log.info(
                "ORDER_EXECUTED event published: orderId={}, executionPrice={}",
                orderId,
                executionPrice
            );

        } catch (OrderNotFoundException |
                 InstrumentNotFoundException e) {

            log.error(
                "Error processing order: orderId={}, error={}",
                orderId,
                e.getMessage()
            );

        } catch (Exception e) {

            log.error(
                "Unexpected error executing order: orderId={}",
                orderId,
                e
            );
        }
    }
}