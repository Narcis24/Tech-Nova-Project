package com.neueda.app.services;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.enums.TimeInForce;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.events.OrderResolved;
import com.neueda.app.exceptions.DuplicateOrderException;
import com.neueda.app.exceptions.InvalidOrderStateException;
import com.neueda.app.exceptions.InsufficientFundsException;
import com.neueda.app.exceptions.InsufficientHoldingsException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    /** Wednesday 2026-10-21, 10:00 in New York: the market is open. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T14:00:00Z"), ZoneOffset.UTC);

    private OrderRepository orderRepository;
    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private InstrumentRepository instrumentRepository;
    private PriceService priceService;
    private EventProducerService eventProducerService;
    private ApplicationEventPublisher events;

    private OrderService orderService;

    @BeforeEach
    void setUp() {

        orderRepository = mock(OrderRepository.class);
        accountRepository = mock(AccountRepository.class);
        positionRepository = mock(PositionRepository.class);
        instrumentRepository = mock(InstrumentRepository.class);
        priceService = mock(PriceService.class);
        eventProducerService = mock(EventProducerService.class);
        events = mock(ApplicationEventPublisher.class);
        when(orderRepository.lockedCash(any())).thenReturn(BigDecimal.ZERO);

        // Real validator over the mocked repositories, so these tests still cover every placement check
        orderService = new OrderService(
            orderRepository,
            new OrderValidator(accountRepository, positionRepository, instrumentRepository, priceService,
                orderRepository, CLOCK),
            eventProducerService,
            events
        );
    }

    // Test methods - DTOs are on another branch (feature/35-increase-junit-tests-coverage)
    // Stub DTOs created locally to allow tests to run
    
    @Test
    void testPlaceOrderRejectsReusedIdempotencyKey() {
        PlaceOrderRequest request = new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "LIMIT", 10, new BigDecimal("150.00"), "key-1");
        when(orderRepository.existsByIdempotencyKey("key-1")).thenReturn(true);

        assertThrows(DuplicateOrderException.class, () -> orderService.placeOrder(request));
        verify(orderRepository, never()).save(any());
    }

    private Account activeAccount() {
        return new Account("12345", "Karl Devon", new BigDecimal("2000.00"),
            AccountStatus.ACTIVE, LocalDateTime.of(2026, 9, 17, 13, 0));
    }

    private Instrument aapl() {
        return new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
    }

    private void stubAccountAndInstrument(Account account, Instrument instrument) {
        when(accountRepository.findById("12345")).thenReturn(Optional.of(account));
        when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.of(instrument));
    }

    @Test
    void testPlaceBuyRejectedWhenCashIsShort() {
        stubAccountAndInstrument(activeAccount(), aapl());

        PlaceOrderRequest request = new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "LIMIT", 100, new BigDecimal("150.00"), "key-cash");

        assertThrows(InsufficientFundsException.class, () -> orderService.placeOrder(request));

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(eventProducerService);
    }

    @Test
    void testPlaceSellRejectedWithoutEnoughShares() {
        stubAccountAndInstrument(activeAccount(), aapl());

        PlaceOrderRequest request = new PlaceOrderRequest(
            "12345", "AAPL", "SELL", "LIMIT", 10, new BigDecimal("150.00"), "key-sell");

        assertThrows(InsufficientHoldingsException.class, () -> orderService.placeOrder(request));

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(eventProducerService);
    }

    @Test
    void testPlaceLimitOrderStaysPending() {
        Account account = activeAccount();
        stubAccountAndInstrument(account, aapl());

        OrderResponse response = orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "LIMIT", 10, new BigDecimal("150.00"), "key-1"));

        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertEquals(OrderType.LIMIT, response.getOrderType());
        assertEquals(new BigDecimal("150.00"), response.getPrice());
        assertNotNull(response.getCreatedOn());
        assertEquals(new BigDecimal("2000.00"), account.getCashBalance());
        verify(orderRepository, times(1)).save(any(Order.class));
        verifyNoInteractions(priceService);
    }

    @Test
    void testPlaceMarketOrderUsesLatestPriceAndStaysPending() {
        Account account = activeAccount();
        stubAccountAndInstrument(account, aapl());
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("150.123456"));

        OrderResponse response = orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "MARKET", 10, null, "key-1"));

        // Filling happens later, when settlement receives the engine's ORDER_EXECUTED event
        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertEquals(OrderType.MARKET, response.getOrderType());
        assertEquals(new BigDecimal("150.12"), response.getPrice());
        assertEquals(new BigDecimal("2000.00"), account.getCashBalance());
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(eventProducerService).publishEvent(
            eq("order-request"), anyString(), eq("ORDER_PLACED"), anyString(), any());
        verifyNoInteractions(positionRepository);
    }

    @Test
    void cancellingAPendingOrderTellsTheEngineAndAnnouncesItResolved() {
        Order order = new Order(UUID.randomUUID(), activeAccount(), aapl(), OrderSide.BUY, OrderType.LIMIT, 10,
            new BigDecimal("150.00"), "key-c", LocalDateTime.of(2026, 10, 20, 14, 0));
        when(orderRepository.changeStatus(order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED)).thenReturn(1);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.cancelOrder(order.getId());

        verify(eventProducerService).publishEvent(
            eq("order-request"), eq(order.getId().toString()), eq("ORDER_CANCELLED"), anyString(), any());
        verify(events).publishEvent(new OrderResolved(order.getId(), OrderStatus.CANCELLED));
    }

    @Test
    void aRefusedCancelTellsNoOne() {
        Order order = new Order(UUID.randomUUID(), activeAccount(), aapl(), OrderSide.BUY, OrderType.LIMIT, 10,
            new BigDecimal("150.00"), "key-d", LocalDateTime.of(2026, 10, 20, 14, 0));
        // already filled: the guarded update moves no row
        when(orderRepository.changeStatus(order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED)).thenReturn(0);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        UUID orderId = order.getId();
        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(orderId));

        verifyNoInteractions(eventProducerService);
        verifyNoInteractions(events);
    }

    @Test
    void placedLimitOrderCarriesItsTimeInForceAndExpiry() {
        stubAccountAndInstrument(activeAccount(), aapl());
        PlaceOrderRequest request = new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "LIMIT", 1, new BigDecimal("150.00"), "key-gtc");
        request.setTimeInForce("GTC");

        OrderResponse response = orderService.placeOrder(request);

        assertEquals(TimeInForce.GTC, response.getTimeInForce());
        assertEquals(Instant.parse("2027-01-19T21:00:00Z"), response.getExpiresAt());
    }

    @Test
    void placedMarketOrderNeverExpires() {
        stubAccountAndInstrument(activeAccount(), aapl());
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("150.00"));

        OrderResponse response = orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "MARKET", 1, null, "key-m"));

        assertNull(response.getTimeInForce());
        assertNull(response.getExpiresAt());
    }

    @Test
    void expiringAPendingOrderTellsTheEngineAndAnnouncesItResolved() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.changeStatus(orderId, OrderStatus.PENDING, OrderStatus.EXPIRED)).thenReturn(1);

        assertTrue(orderService.expireOrder(orderId));

        verify(eventProducerService).publishEvent(
            eq("order-request"), eq(orderId.toString()), eq("ORDER_CANCELLED"), anyString(), any());
        verify(events).publishEvent(new OrderResolved(orderId, OrderStatus.EXPIRED));
    }

    @Test
    void anOrderThatLeftPendingFirstIsNotExpired() {
        UUID orderId = UUID.randomUUID();
        // filled or cancelled just before: the guarded update moves no row
        when(orderRepository.changeStatus(orderId, OrderStatus.PENDING, OrderStatus.EXPIRED)).thenReturn(0);

        assertFalse(orderService.expireOrder(orderId));

        verifyNoInteractions(eventProducerService);
        verifyNoInteractions(events);
    }

    @Test
    void republishPendingSendsEveryPendingOrderToTheEngineAgain() {
        LocalDateTime placed = LocalDateTime.of(2026, 10, 16, 7, 0);
        Order limit = new Order(UUID.randomUUID(), activeAccount(), aapl(), OrderSide.BUY, OrderType.LIMIT, 10,
            new BigDecimal("150.00"), "key-a", placed);
        Order market = new Order(UUID.randomUUID(), activeAccount(), aapl(), OrderSide.BUY, OrderType.MARKET, 5,
            new BigDecimal("151.00"), "key-b", placed);
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(limit, market));

        orderService.republishPending();

        verify(eventProducerService).publishEvent(
            eq("order-request"), eq(limit.getId().toString()), eq("ORDER_PLACED"), anyString(), any());
        verify(eventProducerService).publishEvent(
            eq("order-request"), eq(market.getId().toString()), eq("ORDER_PLACED"), anyString(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void testPlaceMarketOrderFailsWithoutPriceData() {
        stubAccountAndInstrument(activeAccount(), aapl());
        when(priceService.getCurrentPrice("AAPL")).thenThrow(new PriceNotFoundException("No price data"));

        PlaceOrderRequest request = new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "MARKET", 10, null, "key-1");
        assertThrows(PriceNotFoundException.class, () -> orderService.placeOrder(request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void testPlaceLimitOrderRequiresPrice() {
        PlaceOrderRequest request = new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "LIMIT", 10, null, "key-1");
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(request));
    }

    @Test
    void testPlaceMarketOrderRejectsPrice() {
        PlaceOrderRequest request = new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "MARKET", 10, new BigDecimal("150.00"), "key-1");
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(request));
    }

    @Test
    void testPlaceOrderRejectsUnknownOrderType() {
        PlaceOrderRequest request = new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "STOP", 10, new BigDecimal("150.00"), "key-1");
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(request));
    }

}
