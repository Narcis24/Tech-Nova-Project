package com.neueda.app.services;

import com.neueda.app.dtos.OrderExecutedEvent;
import com.neueda.app.dtos.OrderPlacedEvent;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.events.EventEnvelope;
import com.neueda.app.exceptions.OrderNotFoundException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExecutionEngineTest {

    private OrderRepository orderRepository;
    private InstrumentRepository instrumentRepository;
    private PriceService priceService;
    private EventProducerService eventProducerService;
    private ExecutionEngine executionEngine;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        instrumentRepository = mock(InstrumentRepository.class);
        priceService = mock(PriceService.class);
        eventProducerService = mock(EventProducerService.class);

        executionEngine = new ExecutionEngine(
            orderRepository,
            instrumentRepository,
            priceService,
            eventProducerService
        );
    }

    @Test
    void testHandleOrderPlacedPublishesExecutedEventForMarketOrder() {
        UUID orderId = UUID.randomUUID();
        
        Account account = new Account(
            "12345",
            "Karl Devon",
            new BigDecimal("2000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.of(2026, 9, 17, 13, 0)
        );

        Instrument instrument = new Instrument(
            "AAPL",
            "Apple Inc.",
            AssetClass.EQUITY,
            "USD",
            true
        );

        Order order = new Order(
            orderId,
            account,
            instrument,
            OrderSide.BUY,
            OrderType.MARKET,
            10,
            null,  // null price for MARKET
            "key-1",
            LocalDateTime.now()
        );

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("150.00"));

        OrderPlacedEvent event = new OrderPlacedEvent(
            orderId,
            "12345",
            "AAPL",
            "BUY",
            "MARKET",
            10,
            null,
            "key-1"
        );

        EventEnvelope<OrderPlacedEvent> envelope = new EventEnvelope<>(
            UUID.randomUUID().toString(),
            "ORDER_PLACED",
            Instant.now(),
            "OrderService",
            1,
            event
        );

        executionEngine.handleOrderPlaced(envelope);

        // Should publish ORDER_EXECUTED event
        verify(eventProducerService, times(1)).publishEvent(
            eq("tradeEvents"),
            eq(orderId.toString()),
            eq("ORDER_EXECUTED"),
            eq("ExecutionEngine"),
            any(OrderExecutedEvent.class)
        );
    }

    @Test
    void testHandleOrderPlacedChecksLimitOrderTrigger() {
        UUID orderId = UUID.randomUUID();
        
        Account account = new Account("12345", "Karl Devon", new BigDecimal("2000.00"),
            AccountStatus.ACTIVE, LocalDateTime.of(2026, 9, 17, 13, 0));

        Instrument instrument = new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);

        Order order = new Order(
            orderId,
            account,
            instrument,
            OrderSide.BUY,
            OrderType.LIMIT,
            10,
            new BigDecimal("100.00"),  // limit price
            "key-1",
            LocalDateTime.now()
        );

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("150.00"));

        OrderPlacedEvent event = new OrderPlacedEvent(
            orderId,
            "12345",
            "AAPL",
            "BUY",
            "LIMIT",
            10,
            new BigDecimal("100.00"),
            "key-1"
        );

        EventEnvelope<OrderPlacedEvent> envelope = new EventEnvelope<>(
            UUID.randomUUID().toString(),
            "ORDER_PLACED",
            Instant.now(),
            "OrderService",
            1,
            event
        );

        executionEngine.handleOrderPlaced(envelope);

        // Market price (150) is > limit price (100), so LIMIT BUY should NOT trigger
        verify(eventProducerService, never()).publishEvent(
            eq("tradeEvents"),
            anyString(),
            anyString(),
            anyString(),
            any()
        );
    }

    @Test
    void testHandleOrderPlacedExecutesTriggeredLimitOrder() {
        UUID orderId = UUID.randomUUID();
        
        Account account = new Account("12345", "Karl Devon", new BigDecimal("2000.00"),
            AccountStatus.ACTIVE, LocalDateTime.of(2026, 9, 17, 13, 0));

        Instrument instrument = new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);

        Order order = new Order(
            orderId,
            account,
            instrument,
            OrderSide.BUY,
            OrderType.LIMIT,
            10,
            new BigDecimal("150.00"),  // limit price
            "key-1",
            LocalDateTime.now()
        );

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));

        OrderPlacedEvent event = new OrderPlacedEvent(
            orderId,
            "12345",
            "AAPL",
            "BUY",
            "LIMIT",
            10,
            new BigDecimal("150.00"),
            "key-1"
        );

        EventEnvelope<OrderPlacedEvent> envelope = new EventEnvelope<>(
            UUID.randomUUID().toString(),
            "ORDER_PLACED",
            Instant.now(),
            "OrderService",
            1,
            event
        );

        executionEngine.handleOrderPlaced(envelope);

        // Market price (100) <= limit price (150), so LIMIT BUY should trigger
        verify(eventProducerService, times(1)).publishEvent(
            eq("tradeEvents"),
            eq(orderId.toString()),
            eq("ORDER_EXECUTED"),
            eq("ExecutionEngine"),
            any(OrderExecutedEvent.class)
        );
    }
}
