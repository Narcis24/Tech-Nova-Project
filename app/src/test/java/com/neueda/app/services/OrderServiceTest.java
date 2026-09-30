package com.neueda.app.services;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.exceptions.DuplicateOrderException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.producers.OrderPublisher;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import com.fasterxml.jackson.core.JsonProcessingException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private AccountRepository accountRepository;
    private InstrumentRepository instrumentRepository;
    private PriceService priceService;
    private OrderPublisher orderPublisher;

    private OrderService orderService;

    @BeforeEach
    void setUp() {

        orderRepository = mock(OrderRepository.class);
        accountRepository = mock(AccountRepository.class);
        instrumentRepository = mock(InstrumentRepository.class);
        priceService = mock(PriceService.class);
        orderPublisher = mock(OrderPublisher.class);

        orderService = new OrderService(
            orderRepository,
            accountRepository,
            instrumentRepository,
            priceService,
            orderPublisher
        );
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
    void testPlaceOrderRejectsReusedIdempotencyKey() {
        PlaceOrderRequest request = new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "LIMIT", 10, new BigDecimal("150.00"), "key-1");
        when(orderRepository.existsByIdempotencyKey("key-1")).thenReturn(true);

        assertThrows(DuplicateOrderException.class, () -> orderService.placeOrder(request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void testPlaceLimitOrderPublishesToKafka() throws ExecutionException, InterruptedException, java.util.concurrent.TimeoutException, JsonProcessingException {
        Account account = activeAccount();
        Instrument instrument = aapl();
        stubAccountAndInstrument(account, instrument);

        // Mock the save to capture the order
        Order[] saved = new Order[1];
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        OrderResponse response = orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "LIMIT", 10, new BigDecimal("150.00"), "key-1"));

        // With new architecture, order should be published to Kafka
        assertEquals(OrderStatus.PUBLISHED, response.getStatus());
        assertEquals(OrderType.LIMIT, response.getOrderType());
        assertEquals(new BigDecimal("150.00"), response.getPrice());
        
        // Verify order was saved
        verify(orderRepository, times(2)).save(any(Order.class));
        
        // Verify publisher was called
        verify(orderPublisher, times(1)).publishOrder(any());
        
        // Cash should not be debited until execution
        assertEquals(new BigDecimal("2000.00"), account.getCashBalance());
    }

    @Test
    void testPlaceMarketOrderPublishesToKafkaAtLatestPrice() throws ExecutionException, InterruptedException, java.util.concurrent.TimeoutException, JsonProcessingException {
        Account account = activeAccount();
        Instrument instrument = aapl();
        stubAccountAndInstrument(account, instrument);
        
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("150.50"));
        
        Order[] saved = new Order[1];
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        OrderResponse response = orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "MARKET", 10, null, "key-2"));

        // MARKET order should also be published to Kafka (not filled immediately)
        assertEquals(OrderStatus.PUBLISHED, response.getStatus());
        assertEquals(OrderType.MARKET, response.getOrderType());
        assertEquals(new BigDecimal("150.50"), response.getPrice());
        
        // Verify publisher was called with market price
        verify(orderPublisher, times(1)).publishOrder(any());
        verify(priceService, times(1)).getCurrentPrice("AAPL");
    }

    @Test
    void testCancelPublishedOrder() {
        Account account = activeAccount();
        Instrument instrument = aapl();
        UUID orderId = UUID.randomUUID();
        
        Order order = new Order(
            orderId,
            account,
            instrument,
            OrderSide.BUY,
            OrderType.LIMIT,
            10,
            new BigDecimal("150.00"),
            "key-1",
            LocalDateTime.now()
        );
        order.setStatus(OrderStatus.PUBLISHED);
        
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        OrderResponse response = orderService.cancelOrder(orderId);
        
        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testRejectPublishedOrder() {
        Account account = activeAccount();
        Instrument instrument = aapl();
        UUID orderId = UUID.randomUUID();
        
        Order order = new Order(
            orderId,
            account,
            instrument,
            OrderSide.BUY,
            OrderType.LIMIT,
            10,
            new BigDecimal("150.00"),
            "key-1",
            LocalDateTime.now()
        );
        order.setStatus(OrderStatus.PUBLISHED);
        
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        OrderResponse response = orderService.rejectOrder(orderId, "Invalid account");
        
        assertEquals(OrderStatus.REJECTED, response.getStatus());
        assertEquals("Invalid account", response.getMessage());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testGetOrderDetails() {
        Account account = activeAccount();
        Instrument instrument = aapl();
        UUID orderId = UUID.randomUUID();
        
        Order order = new Order(
            orderId,
            account,
            instrument,
            OrderSide.BUY,
            OrderType.LIMIT,
            10,
            new BigDecimal("150.00"),
            "key-1",
            LocalDateTime.now()
        );
        order.setStatus(OrderStatus.PUBLISHED);
        
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        OrderResponse response = orderService.getOrder(orderId);
        
        assertNotNull(response);
        assertEquals(orderId, response.getOrderId());
        assertEquals(OrderStatus.PUBLISHED, response.getStatus());
    }
}
