package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.neueda.events.EventEnvelope;
import com.neueda.events.OrderPlacedEvent;
import com.neueda.events.OrderExecutedEvent;
import com.neueda.events.OrderRejectedEvent;
import com.neueda.events.Topics;
import com.neueda.events.EventTypes;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ExecutionEngine {

    private final ObjectMapper objectMapper;
    private final EventProducerService eventProducerService;
    private final QuoteCache quoteCache;
    private final MarketDataProperties props;

    // Orders waiting for the market, by order id: LIMIT orders not crossed yet, and any order
    // placed while the market is closed. In memory: lost on restart.
    private final Map<UUID, OrderPlacedEvent> resting = new HashMap<>();
    private final Clock clock;
    private final boolean enforceMarketHours;

    @Autowired
    public ExecutionEngine(
            ObjectMapper objectMapper,
            EventProducerService eventProducerService,
            QuoteCache quoteCache,
            MarketDataProperties props,
            @Value("${market-data.enforce-market-hours:true}") boolean enforceMarketHours) {
        this(objectMapper, eventProducerService, quoteCache, props, Clock.systemUTC(), enforceMarketHours);
    }

    /** Tests pass a fixed clock to decide whether the market is open. */
    ExecutionEngine(
            ObjectMapper objectMapper,
            EventProducerService eventProducerService,
            QuoteCache quoteCache,
            MarketDataProperties props,
            Clock clock,
            boolean enforceMarketHours) {

        this.clock = clock;
        this.enforceMarketHours = enforceMarketHours;
        this.objectMapper = objectMapper;
        this.eventProducerService = eventProducerService;
        this.quoteCache = quoteCache;
        this.props = props;
    }

    /**
     * Waiting orders live only in memory, so a restart starts with none. Announcing the start
     * makes the app send every PENDING order again (EngineStartedListener in app).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void announceStarted() {
        log.info("Execution engine started, asking for pending orders");
        eventProducerService.publishEvent(
            Topics.ORDER_EXECUTION,
            "execution-engine",
            EventTypes.ENGINE_STARTED,
            "ExecutionEngine",
            Map.of()
        );
    }

    @KafkaListener(
        topics = Topics.ORDER_REQUEST,
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
        } catch (JacksonException e) {

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
        if (!EventTypes.ORDER_PLACED.equals(envelope.eventType())) {

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
        } catch (JacksonException e) {

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
            event.orderId(),
            event.accountId(),
            event.symbol(),
            event.side(),
            event.orderType(),
            event.quantity(),
            event.price()
        );

        handle(event);
    }

    /**
     * Every new quote re-checks the orders resting on that symbol, so there is no
     * separate schedule. Own consumer group: every executor instance needs every quote.
     */
    @KafkaListener(topics = MarketDataPoller.TOPIC, groupId = "execution-engine-quotes")
    public void onQuote(String message) {
        Quote quote = objectMapper.treeToValue(objectMapper.readTree(message).path("payload"), Quote.class);
        quoteCache.put(quote);
        recheckResting(quote);
    }

    /**
     * Fills what it can, rests a LIMIT that has not crossed yet, rejects what can never be
     * priced, so an order is never left PENDING without a reason. While the market is closed,
     * every order rests until the first quote after the open.
     */
    synchronized void handle(OrderPlacedEvent event) {
        if (!marketOpen()) {
            resting.put(event.orderId(), event);
            log.info("Market closed, order waits for the open: orderId={}, symbol={}", event.orderId(), event.symbol());
            return;
        }
        boolean market = "MARKET".equals(event.orderType());
        // The injected clock, never the system time, so a replay of a recorded day decides the same way
        FillRule.Decision decision = quoteCache
            .fresh(event.symbol(), clock.instant(), Duration.ofSeconds(props.maxQuoteAgeSeconds()))
            .<FillRule.Decision>map(q -> FillRule.decide(event, q))
            .orElseGet(() -> market
                ? new FillRule.Reject("No fresh quote for " + event.symbol())
                : new FillRule.Wait());

        switch (decision) {
            case FillRule.Fill(BigDecimal price) -> publishFill(event, price);
            case FillRule.Reject(String reason) -> publishReject(event, reason);
            case FillRule.Wait() -> {
                resting.put(event.orderId(), event);
                log.info("Resting LIMIT order: orderId={}, symbol={}", event.orderId(), event.symbol());
            }
        }
    }

    private synchronized void recheckResting(Quote quote) {
        // Quotes keep coming while the market is closed, but they are last session's prices
        if (!marketOpen()) {
            return;
        }
        for (OrderPlacedEvent order : List.copyOf(resting.values())) {
            if (!order.symbol().equals(quote.symbol())) {
                continue;
            }
            switch (FillRule.decide(order, quote)) {
                case FillRule.Fill(BigDecimal price) -> {
                    resting.remove(order.orderId());
                    publishFill(order, price);
                }
                // A MARKET order queued overnight that still cannot be priced
                case FillRule.Reject(String reason) -> {
                    resting.remove(order.orderId());
                    publishReject(order, reason);
                }
                case FillRule.Wait() -> {
                    // stays resting
                }
            }
        }
    }

    /** The E2E tests turn the hours off: they run at any time against a stub market. */
    private boolean marketOpen() {
        return !enforceMarketHours || MarketHours.isOpen(clock.instant());
    }

    private void publishReject(OrderPlacedEvent event, String reason) {
        log.info("Rejecting order: orderId={}, reason={}", event.orderId(), reason);
        eventProducerService.publishEvent(
            Topics.ORDER_EXECUTION,
            event.orderId().toString(),
            EventTypes.ORDER_REJECTED,
            "ExecutionEngine",
            new OrderRejectedEvent(event.orderId(), event.accountId(), event.symbol(), reason)
        );
    }

    private void publishFill(OrderPlacedEvent event, BigDecimal executionPrice) {
        BigDecimal totalValue = executionPrice.multiply(BigDecimal.valueOf(event.quantity()));

        eventProducerService.publishEvent(
            Topics.ORDER_EXECUTION,
            event.orderId().toString(),
            EventTypes.ORDER_EXECUTED,
            "ExecutionEngine",
            new OrderExecutedEvent(
                event.orderId(),
                event.accountId(),
                event.symbol(),
                event.side(),
                event.quantity(),
                executionPrice,
                totalValue
            )
        );

        log.info(
            "Published ORDER_EXECUTED: orderId={}, executionPrice={}, totalValue={}",
            event.orderId(),
            executionPrice,
            totalValue
        );
    }
}
