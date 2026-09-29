package com.neueda.app.services;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.OrderExecutedEvent;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.events.EventEnvelope;
import com.neueda.app.exceptions.OrderNotTriggeredException;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.exceptions.DuplicateOrderException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.models.Position;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private InstrumentRepository instrumentRepository;
    private PriceService priceService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {

        orderRepository = mock(OrderRepository.class);
        accountRepository = mock(AccountRepository.class);
        positionRepository = mock(PositionRepository.class);
        instrumentRepository = mock(InstrumentRepository.class);
        priceService = mock(PriceService.class);
        EventProducerService eventProducerService = mock(EventProducerService.class);  // ADD THIS

        orderService = new OrderService(
            orderRepository,
            accountRepository,
            positionRepository,
            instrumentRepository,
            priceService,
            eventProducerService  
        );
    }

    // Test methods - DTOs are on another branch (feature/35-increase-junit-tests-coverage)
    // Stub DTOs created locally to allow tests to run
    
    @Test
    void testExecuteBuyOrderSuccessfully() {

        // Arrange
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
            OrderType.LIMIT,
            10,
            new BigDecimal("100.00"),
            UUID.randomUUID().toString(),
            LocalDateTime.now()
        );

        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        when(accountRepository.findById("12345"))
            .thenReturn(Optional.of(account));

        when(instrumentRepository.findBySymbol("AAPL"))
            .thenReturn(Optional.of(instrument));

        when(positionRepository.findByAccountIdAndSymbol("12345", "AAPL"))
            .thenReturn(Optional.empty());


        // Act
        orderService.executeOrder(orderId);


        // Assert
        assertEquals(
            new BigDecimal("1000.00"),
            account.getCashBalance()
        );

    }

    @Test
    void testSellOrderSuccessfully() {
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
            OrderSide.SELL,
            OrderType.LIMIT,
            10,
            new BigDecimal("100.00"),
            UUID.randomUUID().toString(),
            LocalDateTime.now()
        );

        Position position = new Position(
            account,
            instrument,
            20,
            new BigDecimal("80.00")
        );

        // When the service looks for the order
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        // When the service looks for the instrument
        when(instrumentRepository.findBySymbol("AAPL"))
            .thenReturn(Optional.of(instrument));

        // When the service looks for the position
        when(positionRepository.findByAccountIdAndSymbol("12345", "AAPL"))
            .thenReturn(Optional.of(position));

        // When the service looks for the account
        when(accountRepository.findById("12345"))
            .thenReturn(Optional.of(account));

        // Execute the SELL order
        orderService.executeOrder(orderId);

        // 10 shares × €100 = €1,000
        // Account should increase from €2,000 to €3,000
        assertEquals(
            new BigDecimal("3000.00"),
            account.getCashBalance()
        );

        // Position should decrease from 20 shares to 10
        assertEquals(10, position.getQuantity());

        // Order should now be filled
        assertEquals(OrderStatus.FILLED, order.getStatus());

    }

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
    void testPlaceLimitOrderStaysPending() {
        Account account = activeAccount();
        stubAccountAndInstrument(account, aapl());

        OrderResponse response = orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "LIMIT", 10, new BigDecimal("150.00"), "key-1"));

        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertEquals(OrderType.LIMIT, response.getOrderType());
        assertEquals(new BigDecimal("150.00"), response.getPrice());
        assertEquals(new BigDecimal("2000.00"), account.getCashBalance());
        verify(orderRepository, times(1)).save(any(Order.class));
        verifyNoInteractions(priceService);
    }

    @Test
    void testPlaceMarketOrderStaysPendingAsync() {
        Account account = activeAccount();
        stubAccountAndInstrument(account, aapl());
        EventProducerService eventProducer = mock(EventProducerService.class);
        
        // Recreate service with mocked event producer
        orderService = new OrderService(
            orderRepository,
            accountRepository,
            positionRepository,
            instrumentRepository,
            priceService,
            eventProducer
        );

        Order[] saved = new Order[1];
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        OrderResponse response = orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "MARKET", 10, null, "key-1"));

        // Market orders stay PENDING until ExecutionEngine processes them
        assertEquals(OrderStatus.PENDING, response.getStatus());
        // Market orders have null price until execution
        assertNull(response.getPrice());
        // Account cash is NOT changed during placeOrder (happens during execution)
        assertEquals(new BigDecimal("2000.00"), account.getCashBalance());
        // Should publish ORDER_PLACED event
        verify(eventProducer, times(1)).publishEvent(
            eq("trades"),
            any(String.class),
            eq("ORDER_PLACED"),
            eq("OrderService"),
            any()
        );
        // Position should NOT be created yet
        verify(positionRepository, never()).save(any(Position.class));
    }

    @Test
    void testPlaceMarketOrderNoLongerFetchesPriceSync() {
        stubAccountAndInstrument(activeAccount(), aapl());
        // PriceService should NOT be called during placeOrder
        
        orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "MARKET", 10, null, "key-1"));

        // Verify priceService was never called - execution engine will fetch the price
        verifyNoInteractions(priceService);
    }

    @Test
    void testPlaceLimitOrderRequiresPrice() {
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "LIMIT", 10, null, "key-1")));
    }

    @Test
    void testPlaceMarketOrderRejectsPrice() {
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "MARKET", 10, new BigDecimal("150.00"), "key-1")));
    }

    @Test
    void testPlaceOrderRejectsUnknownOrderType() {
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "STOP", 10, new BigDecimal("150.00"), "key-1")));
    }

    @Test
    void testExecuteRefusesLimitOrderThatHasNotReachedItsLimit() {
        UUID orderId = UUID.randomUUID();
        Account account = activeAccount();
        Instrument instrument = aapl();
        Order order = new Order(orderId, account, instrument, OrderSide.BUY, OrderType.LIMIT, 10,
            new BigDecimal("100.00"), "key-1", LocalDateTime.now());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("150.00"));

        assertThrows(OrderNotTriggeredException.class, () -> orderService.executeOrder(orderId));

        assertEquals(new BigDecimal("2000.00"), account.getCashBalance());
        verifyNoInteractions(positionRepository);
    }

    @Test
    void testHandleOrderExecutedProcessesBuyExecution() {
        UUID orderId = UUID.randomUUID();
        Account account = activeAccount();
        Instrument instrument = aapl();
        Order order = new Order(orderId, account, instrument, OrderSide.BUY, OrderType.MARKET,
            10, null, "key-1", LocalDateTime.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accountRepository.findById("12345")).thenReturn(Optional.of(account));
        when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.of(instrument));
        when(positionRepository.findByAccountIdAndSymbol("12345", "AAPL")).thenReturn(Optional.empty());

        OrderExecutedEvent event = new OrderExecutedEvent(
            orderId,
            "12345",
            "AAPL",
            "BUY",
            10,
            new BigDecimal("150.00"),
            new BigDecimal("1500.00")
        );

        EventEnvelope<OrderExecutedEvent> envelope = new EventEnvelope<>(
            UUID.randomUUID().toString(),
            "ORDER_EXECUTED",
            Instant.now(),
            "ExecutionEngine",
            1,
            event
        );

        orderService.handleOrderExecuted(envelope);

        // Order should be marked as FILLED
        assertEquals(OrderStatus.FILLED, order.getStatus());
        // Price should be set to execution price
        assertEquals(new BigDecimal("150.00"), order.getPrice());
        // Account cash should be debited
        assertEquals(new BigDecimal("500.00"), account.getCashBalance());
        // Repository saves should be called
        verify(orderRepository).save(order);
        verify(accountRepository).save(account);
    }

    @Test
    void testHandleOrderExecutedProcessesSellExecution() {
        UUID orderId = UUID.randomUUID();
        Account account = activeAccount();
        Instrument instrument = aapl();
        Order order = new Order(orderId, account, instrument, OrderSide.SELL, OrderType.MARKET,
            5, null, "key-1", LocalDateTime.now());

        Position position = new Position(account, instrument, 20, new BigDecimal("100.00"));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accountRepository.findById("12345")).thenReturn(Optional.of(account));
        when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.of(instrument));
        when(positionRepository.findByAccountIdAndSymbol("12345", "AAPL")).thenReturn(Optional.of(position));

        OrderExecutedEvent event = new OrderExecutedEvent(
            orderId,
            "12345",
            "AAPL",
            "SELL",
            5,
            new BigDecimal("160.00"),
            new BigDecimal("800.00")
        );

        EventEnvelope<OrderExecutedEvent> envelope = new EventEnvelope<>(
            UUID.randomUUID().toString(),
            "ORDER_EXECUTED",
            Instant.now(),
            "ExecutionEngine",
            1,
            event
        );

        orderService.handleOrderExecuted(envelope);

        // Order should be marked as FILLED
        assertEquals(OrderStatus.FILLED, order.getStatus());
        // Price should be set to execution price
        assertEquals(new BigDecimal("160.00"), order.getPrice());
        // Position quantity should be reduced
        assertEquals(15, position.getQuantity());
        // Account cash should be credited
        assertEquals(new BigDecimal("2800.00"), account.getCashBalance());
        // Repository saves should be called
        verify(orderRepository).save(order);
        verify(accountRepository).save(account);
        verify(positionRepository).save(position);
    }
}
