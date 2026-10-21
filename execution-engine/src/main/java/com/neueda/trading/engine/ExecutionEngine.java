package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import com.neueda.events.OrderCancelledEvent;
import com.neueda.events.OrderPlacedEvent;
import com.neueda.events.OrderExecutedEvent;
import com.neueda.events.OrderRejectedEvent;
import com.neueda.events.Topics;
import com.neueda.events.EventTypes;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ExecutionEngine {

    private static final String SOURCE = "ExecutionEngine";

    private final ObjectMapper objectMapper;
    private final EventProducerService eventProducerService;
    private final QuoteCache quoteCache;
    private final MarketDataProperties props;

    // Orders waiting for the market, by order id: LIMIT orders not crossed yet, and any order
    // placed while the market is closed. In memory: lost on restart.
    private final Map<UUID, OrderPlacedEvent> resting = new HashMap<>();
    private final Clock clock;
    private final boolean enforceMarketHours;
    private final DecisionJournal journal;

    @Autowired
    public ExecutionEngine(
            ObjectMapper objectMapper,
            EventProducerService eventProducerService,
            QuoteCache quoteCache,
            MarketDataProperties props,
            DecisionJournal journal,
            @Value("${market-data.enforce-market-hours:true}") boolean enforceMarketHours) {
        this(objectMapper, eventProducerService, quoteCache, props, journal, Clock.systemUTC(), enforceMarketHours);
    }

    /** Tests pass a fixed clock to decide whether the market is open. */
    ExecutionEngine(
            ObjectMapper objectMapper,
            EventProducerService eventProducerService,
            QuoteCache quoteCache,
            MarketDataProperties props,
            DecisionJournal journal,
            Clock clock,
            boolean enforceMarketHours) {

        this.journal = journal;
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
        // The settings that change decisions, so a replay of this run uses the same ones
        journal(JournalTypes.ENGINE_STARTED, Map.of(
            "maxQuoteAgeSeconds", props.maxQuoteAgeSeconds(),
            "enforceMarketHours", enforceMarketHours));
        eventProducerService.publishEvent(
            Topics.ORDER_EXECUTION,
            "execution-engine",
            EventTypes.ENGINE_STARTED,
            SOURCE,
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
         * This consumer handles ORDER_PLACED, and ORDER_CANCELLED for orders still waiting here.
         */
        if (EventTypes.ORDER_CANCELLED.equals(envelope.eventType())) {
            OrderCancelledEvent cancelled;
            try {
                cancelled = objectMapper.convertValue(envelope.payload(), OrderCancelledEvent.class);
            } catch (JacksonException e) {
                log.error("Failed to convert payload into OrderCancelledEvent: {}", envelope.payload(), e);
                throw new EventProcessingException("Failed to deserialize OrderCancelledEvent", e);
            }
            cancel(cancelled.orderId());
            return;
        }

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
        accept(quote);
    }

    /** Synchronized with orders, so the journal holds quotes and orders in the order they were handled. */
    synchronized void accept(Quote quote) {
        journal(JournalTypes.QUOTE_RECEIVED, Map.of("quote", quote));
        quoteCache.put(quote);
        recheckResting(quote);
    }

    /**
     * The holder cancelled the order: stop waiting on it, so no later quote can fill it. Journalled even
     * when the order is not waiting here (already filled, or unknown), since it is an input a replay needs.
     */
    synchronized void cancel(UUID orderId) {
        journal(JournalTypes.ORDER_CANCELLED, Map.of("orderId", orderId));
        if (resting.remove(orderId) != null) {
            log.info("Cancelled order no longer waits: orderId={}", orderId);
        }
    }

    /**
     * Fills what it can, rests a LIMIT that has not crossed yet, rejects what can never be
     * priced, so an order is never left PENDING without a reason. While the market is closed,
     * every order rests until the first quote after the open.
     */
    synchronized void handle(OrderPlacedEvent event) {
        journal(JournalTypes.ORDER_RECEIVED, Map.of("order", event));
        if (!marketOpen()) {
            journalDecision(event, JournalTypes.QUEUED_MARKET_CLOSED, null, null);
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
            case FillRule.Fill(BigDecimal price) -> {
                journalDecision(event, JournalTypes.FILL, price, null);
                publishFill(event, price);
            }
            case FillRule.Reject(String reason) -> {
                journalDecision(event, JournalTypes.REJECT, null, reason);
                publishReject(event, reason);
            }
            case FillRule.Wait() -> {
                journalDecision(event, JournalTypes.WAIT, null, null);
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
                    journalDecision(order, JournalTypes.FILL, price, null);
                    publishFill(order, price);
                }
                // A MARKET order queued overnight that still cannot be priced
                case FillRule.Reject(String reason) -> {
                    resting.remove(order.orderId());
                    journalDecision(order, JournalTypes.REJECT, null, reason);
                    publishReject(order, reason);
                }
                case FillRule.Wait() -> {
                    // stays resting; not journalled on every quote, a replay re-runs the checks itself
                }
            }
        }
    }

    private void journal(String type, Map<String, Object> fields) {
        journal.write(clock.instant(), type, fields);
    }

    private void journalDecision(OrderPlacedEvent order, String decision, BigDecimal price, String reason) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("orderId", order.orderId());
        fields.put("decision", decision);
        if (price != null) {
            fields.put("price", price);
        }
        if (reason != null) {
            fields.put("reason", reason);
        }
        journal(JournalTypes.DECISION, fields);
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
            SOURCE,
            new OrderRejectedEvent(event.orderId(), event.accountId(), event.symbol(), reason)
        );
    }

    private void publishFill(OrderPlacedEvent event, BigDecimal executionPrice) {
        BigDecimal totalValue = executionPrice.multiply(BigDecimal.valueOf(event.quantity()));

        eventProducerService.publishEvent(
            Topics.ORDER_EXECUTION,
            event.orderId().toString(),
            EventTypes.ORDER_EXECUTED,
            SOURCE,
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
