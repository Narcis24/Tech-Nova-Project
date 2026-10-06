package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.neueda.trading.events.EventEnvelope;
import com.neueda.trading.events.OrderExecutedEvent;
import com.neueda.trading.events.OrderPlacedEvent;
import com.neueda.trading.events.OrderRejectedEvent;

class ExecutionEngineTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final UUID orderId = UUID.randomUUID();
    private EventProducerService producer;
    private QuoteCache cache;
    private ExecutionEngine engine;

    @BeforeEach
    void setUp() {
        producer = mock(EventProducerService.class);
        cache = new QuoteCache();
        MarketDataProperties props =
            new MarketDataProperties(List.of("AAPL"), 120, 25, 2000, 600, "http://x", "", "");
        engine = new ExecutionEngine(mapper, producer, cache, props);
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

    private String quoteMessage(String bid, String ask) throws Exception {
        return mapper.writeValueAsString(new EventEnvelope<>("q", "MARKET_DATA", Instant.now(), "p", 1,
            new Quote("AAPL", new BigDecimal(bid), new BigDecimal(ask), Instant.now())));
    }
}
