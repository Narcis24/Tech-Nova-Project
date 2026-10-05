package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.trading.events.EventEnvelope;
import com.neueda.trading.events.OrderPlacedEvent;
import com.neueda.trading.events.OrderExecutedEvent;
import com.neueda.trading.events.OrderRejectedEvent;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ExecutionEngine {

    private final ObjectMapper objectMapper;
    private final EventProducerService eventProducerService;
    private final QuoteCache quoteCache;
    private final MarketDataProperties props;

    public ExecutionEngine(
            ObjectMapper objectMapper,
            EventProducerService eventProducerService,
            QuoteCache quoteCache,
            MarketDataProperties props) {

        this.objectMapper = objectMapper;
        this.eventProducerService = eventProducerService;
        this.quoteCache = quoteCache;
        this.props = props;
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
         * Price against the latest live quote. Anything that cannot be priced is
         * rejected, so the order is always resolved and never left PENDING.
         */
        Optional<Quote> quote = quoteCache.fresh(
            event.getSymbol(), Instant.now(), Duration.ofSeconds(props.maxQuoteAgeSeconds()));

        FillRule.Decision decision = quote
            .<FillRule.Decision>map(q -> FillRule.decide(event, q))
            .orElseGet(() -> new FillRule.Reject("No fresh quote for " + event.getSymbol()));

        if (decision instanceof FillRule.Reject reject) {
            log.info("Rejecting order: orderId={}, reason={}", event.getOrderId(), reject.reason());
            eventProducerService.publishEvent(
                "order-execution",
                event.getOrderId().toString(),
                "ORDER_REJECTED",
                "ExecutionEngine",
                new OrderRejectedEvent(
                    event.getOrderId(), event.getAccountId(), event.getSymbol(), reject.reason())
            );
            return;
        }

        BigDecimal executionPrice = ((FillRule.Fill) decision).price();

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