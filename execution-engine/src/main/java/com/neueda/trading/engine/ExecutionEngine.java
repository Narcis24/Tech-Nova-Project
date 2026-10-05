package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    // LIMIT orders waiting for the market, by order id. In memory: lost on restart.
    private final Map<UUID, OrderPlacedEvent> resting = new HashMap<>();

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
        } catch (JsonProcessingException e) {

            log.error(
                "Failed to deserialize Kafka message into EventEnvelope: {}",
                message,
                e
            );

            throw new EventProcessingException(
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
        } catch (IllegalArgumentException e) {

            log.error(
                "Failed to convert payload into OrderPlacedEvent: {}",
                envelope.payload(),
                e
            );

            throw new EventProcessingException(
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

        handle(event);
    }

    /**
     * Every new quote re-checks the LIMIT orders resting on that symbol, so there is no
     * separate schedule. Own consumer group: every executor instance needs every quote.
     */
    @KafkaListener(topics = MarketDataPoller.TOPIC, groupId = "execution-engine-quotes")
    public void onQuote(String message) throws JsonProcessingException {
        Quote quote = objectMapper.treeToValue(objectMapper.readTree(message).path("payload"), Quote.class);
        quoteCache.put(quote);
        recheckResting(quote);
    }

    /**
     * Fills what it can, rests a LIMIT that has not crossed yet, rejects what can never be
     * priced, so an order is never left PENDING without a reason.
     */
    synchronized void handle(OrderPlacedEvent event) {
        boolean market = "MARKET".equals(event.getOrderType());
        FillRule.Decision decision = quoteCache
            .fresh(event.getSymbol(), Instant.now(), Duration.ofSeconds(props.maxQuoteAgeSeconds()))
            .<FillRule.Decision>map(q -> FillRule.decide(event, q))
            .orElseGet(() -> market
                ? new FillRule.Reject("No fresh quote for " + event.getSymbol())
                : new FillRule.Wait());

        switch (decision) {
            case FillRule.Fill(BigDecimal price) -> publishFill(event, price);
            case FillRule.Reject(String reason) -> publishReject(event, reason);
            case FillRule.Wait() -> {
                resting.put(event.getOrderId(), event);
                log.info("Resting LIMIT order: orderId={}, symbol={}", event.getOrderId(), event.getSymbol());
            }
        }
    }

    private synchronized void recheckResting(Quote quote) {
        for (OrderPlacedEvent order : List.copyOf(resting.values())) {
            if (order.getSymbol().equals(quote.symbol())
                    && FillRule.decide(order, quote) instanceof FillRule.Fill(BigDecimal price)) {
                resting.remove(order.getOrderId());
                publishFill(order, price);
            }
        }
    }

    private void publishReject(OrderPlacedEvent event, String reason) {
        log.info("Rejecting order: orderId={}, reason={}", event.getOrderId(), reason);
        eventProducerService.publishEvent(
            "order-execution",
            event.getOrderId().toString(),
            "ORDER_REJECTED",
            "ExecutionEngine",
            new OrderRejectedEvent(event.getOrderId(), event.getAccountId(), event.getSymbol(), reason)
        );
    }

    private void publishFill(OrderPlacedEvent event, BigDecimal executionPrice) {
        BigDecimal totalValue = executionPrice.multiply(BigDecimal.valueOf(event.getQuantity()));

        eventProducerService.publishEvent(
            "order-execution",
            event.getOrderId().toString(),
            "ORDER_EXECUTED",
            "ExecutionEngine",
            new OrderExecutedEvent(
                event.getOrderId(),
                event.getAccountId(),
                event.getSymbol(),
                event.getSide(),
                event.getQuantity(),
                executionPrice,
                totalValue
            )
        );

        log.info(
            "Published ORDER_EXECUTED: orderId={}, executionPrice={}, totalValue={}",
            event.getOrderId(),
            executionPrice,
            totalValue
        );
    }
}
