package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.neueda.events.EventEnvelope;
import com.neueda.events.OrderExecutedEvent;
import com.neueda.events.OrderPlacedEvent;
import com.neueda.events.OrderRejectedEvent;

class ExecutionEngineTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final UUID orderId = UUID.randomUUID();
    private EventProducerService producer;
    private QuoteCache cache;
    private ExecutionEngine engine;
    private Clock clock;

    // Wednesday 11:00 and Friday 03:24 in New York
    private static final Instant MARKET_OPEN = Instant.parse("2026-10-14T15:00:00Z");
    private static final Instant MARKET_CLOSED = Instant.parse("2026-10-16T07:24:00Z");

    @BeforeEach
    void setUp() {
        producer = mock(EventProducerService.class);
        cache = new QuoteCache();
        MarketDataProperties props =
            new MarketDataProperties(List.of("AAPL"), 120, 25, 2000, 600, "http://x", "", "");
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(MARKET_OPEN);
        engine = new ExecutionEngine(mapper, producer, cache, props, clock, true);
    }

    private void place(String type, String limit) throws Exception {
        OrderPlacedEvent placed = new OrderPlacedEvent(orderId, "ACC1", "AAPL", "BUY", type, 10,
            limit == null ? null : new BigDecimal(limit), "key");
        engine.handleOrderPlaced(mapper.writeValueAsString(
            new EventEnvelope<>("e1", "ORDER_PLACED", Instant.now(), "app", 1, placed)));
    }

    @Test
    void ordersWithoutAQuoteAreRejectedNotLeftPending() throws Exception {
        place("MARKET", "150.00");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(producer).publishEvent(eq("order-execution"), eq(orderId.toString()),
            eq("ORDER_REJECTED"), any(), payload.capture());
        assertEquals(orderId, ((OrderRejectedEvent) payload.getValue()).orderId());
    }

    @Test
    void malformedPayloadFailsTheRecordInsteadOfBeingSkipped() {
        // the error handler retries, then dead-letters the record; it must not vanish silently
        String bad = mapper.writeValueAsString(new EventEnvelope<>("e1", "ORDER_PLACED", Instant.now(), "app", 1,
            Map.of("orderId", "not-a-uuid")));

        assertThrows(EventProcessingException.class, () -> engine.handleOrderPlaced(bad));
        verify(producer, never()).publishEvent(any(), any(), any(), any(), any());
    }

    @Test
    void marketOrderFillsAtTheLiveAsk() throws Exception {
        cache.put(new Quote("AAPL", new BigDecimal("99.50"), new BigDecimal("100.50"), Instant.now()));

        place("MARKET", "150.00");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(producer).publishEvent(eq("order-execution"), any(), eq("ORDER_EXECUTED"), any(),
            payload.capture());
        OrderExecutedEvent fill = (OrderExecutedEvent) payload.getValue();
        assertEquals(new BigDecimal("100.50"), fill.executionPrice());
        assertEquals(new BigDecimal("1005.00"), fill.totalValue());
    }

    @Test
    void limitThatDoesNotCrossRestsThenFillsWhenAQuoteCrossesIt() throws Exception {
        cache.put(new Quote("AAPL", new BigDecimal("99.50"), new BigDecimal("100.50"), Instant.now()));

        place("LIMIT", "90.00");

        verify(producer, never()).publishEvent(any(), any(), any(), any(), any());

        // market drops: ask 89.00 is at or below the 90.00 limit
        engine.onQuote(quoteMessage("88.50", "89.00"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(producer).publishEvent(eq("order-execution"), eq(orderId.toString()),
            eq("ORDER_EXECUTED"), any(), payload.capture());
        assertEquals(new BigDecimal("89.00"), ((OrderExecutedEvent) payload.getValue()).executionPrice());

        // filled once: a further quote does not fill it again
        engine.onQuote(quoteMessage("80.00", "81.00"));
        verify(producer, times(1)).publishEvent(any(), any(), eq("ORDER_EXECUTED"), any(), any());
    }

    @Test
    void limitWithNoQuoteYetRests() throws Exception {
        place("LIMIT", "90.00");

        verify(producer, never()).publishEvent(any(), any(), any(), any(), any());
    }

    @Test
    void quotesForOtherSymbolsDoNotFillRestingOrders() throws Exception {
        place("LIMIT", "90.00");

        engine.onQuote(mapper.writeValueAsString(new EventEnvelope<>("q", "MARKET_DATA", Instant.now(), "p", 1,
            new Quote("MSFT", new BigDecimal("1.00"), new BigDecimal("1.00"), Instant.now()))));

        verify(producer, never()).publishEvent(any(), any(), any(), any(), any());
    }

    @Test
    void ignoresNonOrderPlacedEvents() throws Exception {
        String message = mapper.writeValueAsString(new EventEnvelope<>("e1", "ORDER_EXECUTED", Instant.now(), "app", 1,
            Map.of("orderId", "123")));

        // Should not throw
        engine.handleOrderPlaced(message);

        verify(producer, never()).publishEvent(any(), any(), any(), any(), any());
    }

    @Test
    void marketSellOrderFillsAtTheLiveBid() throws Exception {
        cache.put(new Quote("AAPL", new BigDecimal("99.50"), new BigDecimal("100.50"), Instant.now()));

        UUID sellOrderId = UUID.randomUUID();
        OrderPlacedEvent sellOrder = new OrderPlacedEvent(sellOrderId, "ACC1", "AAPL", "SELL", "MARKET", 10,
            null, "key");
        engine.handleOrderPlaced(mapper.writeValueAsString(
            new EventEnvelope<>("e1", "ORDER_PLACED", Instant.now(), "app", 1, sellOrder)));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(producer).publishEvent(eq("order-execution"), any(), eq("ORDER_EXECUTED"), any(),
            payload.capture());
        OrderExecutedEvent fill = (OrderExecutedEvent) payload.getValue();
        assertEquals(new BigDecimal("99.50"), fill.executionPrice());
        assertEquals(new BigDecimal("995.00"), fill.totalValue());
        assertEquals(sellOrderId, fill.orderId());
    }

    @Test
    void invalidMessageThrowsEventProcessingException() {
        String invalidJson = "not a valid json";

        assertThrows(EventProcessingException.class, () -> engine.handleOrderPlaced(invalidJson));
    }

    @Test
    void marketOrderWithZeroBidAskIsRejected() throws Exception {
        cache.put(new Quote("AAPL", BigDecimal.ZERO, BigDecimal.ZERO, Instant.now()));

        place("MARKET", "150.00");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(producer).publishEvent(eq("order-execution"), eq(orderId.toString()),
            eq("ORDER_REJECTED"), any(), payload.capture());
    }

    @Test
    void marketOrderPlacedWhileClosedWaitsAndFillsOnTheFirstQuoteAfterTheOpen() throws Exception {
        when(clock.instant()).thenReturn(MARKET_CLOSED);
        cache.put(new Quote("AAPL", new BigDecimal("99.50"), new BigDecimal("100.50"), Instant.now()));

        place("MARKET", null);
        engine.onQuote(quoteMessage("99.00", "100.00"));
        verify(producer, never()).publishEvent(any(), any(), any(), any(), any());

        when(clock.instant()).thenReturn(MARKET_OPEN);
        engine.onQuote(quoteMessage("101.00", "102.00"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(producer).publishEvent(eq("order-execution"), eq(orderId.toString()),
            eq("ORDER_EXECUTED"), any(), payload.capture());
        assertEquals(new BigDecimal("102.00"), ((OrderExecutedEvent) payload.getValue()).executionPrice());
    }

    @Test
    void limitPlacedWhileClosedFillsOnlyOnceOpenAndCrossed() throws Exception {
        when(clock.instant()).thenReturn(MARKET_CLOSED);
        place("LIMIT", "90.00");

        // crosses, but the market is closed
        engine.onQuote(quoteMessage("88.50", "89.00"));
        verify(producer, never()).publishEvent(any(), any(), any(), any(), any());

        when(clock.instant()).thenReturn(MARKET_OPEN);
        // open, but the ask is above the limit
        engine.onQuote(quoteMessage("94.50", "95.00"));
        verify(producer, never()).publishEvent(any(), any(), any(), any(), any());

        engine.onQuote(quoteMessage("88.50", "89.00"));
        verify(producer, times(1)).publishEvent(any(), any(), eq("ORDER_EXECUTED"), any(), any());
    }

    @Test
    void marketOrderQueuedOvernightIsRejectedIfTheFirstOpenQuoteHasNoPrice() throws Exception {
        when(clock.instant()).thenReturn(MARKET_CLOSED);
        place("MARKET", null);

        when(clock.instant()).thenReturn(MARKET_OPEN);
        engine.onQuote(quoteMessage("0", "0"));

        verify(producer).publishEvent(eq("order-execution"), eq(orderId.toString()),
            eq("ORDER_REJECTED"), any(), any());
    }

    @Test
    void withMarketHoursOffOrdersFillAtAnyTime() throws Exception {
        MarketDataProperties props =
            new MarketDataProperties(List.of("AAPL"), 120, 25, 2000, 600, "http://x", "", "");
        engine = new ExecutionEngine(mapper, producer, cache, props, clock, false);
        when(clock.instant()).thenReturn(MARKET_CLOSED);
        cache.put(new Quote("AAPL", new BigDecimal("99.50"), new BigDecimal("100.50"), Instant.now()));

        place("MARKET", null);

        verify(producer).publishEvent(any(), any(), eq("ORDER_EXECUTED"), any(), any());
    }

    @Test
    void announcesItsStartSoTheAppSendsPendingOrdersAgain() {
        engine.announceStarted();
        verify(producer).publishEvent(eq("order-execution"), any(), eq("ENGINE_STARTED"), any(), any());
    }

    private String quoteMessage(String bid, String ask) throws Exception {
        return mapper.writeValueAsString(new EventEnvelope<>("q", "MARKET_DATA", Instant.now(), "p", 1,
            new Quote("AAPL", new BigDecimal(bid), new BigDecimal(ask), Instant.now())));
    }
}
