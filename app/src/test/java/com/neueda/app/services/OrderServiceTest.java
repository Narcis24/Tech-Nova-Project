package com.neueda.app.services;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.dtos.OrderResponse;
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
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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

        orderService = new OrderService(
            orderRepository,
            accountRepository,
            positionRepository,
            instrumentRepository,
            priceService
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
            "Karl", "Devon",
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
            "Karl", "Devon",
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
        return new Account("12345", "Karl", "Devon", new BigDecimal("2000.00"),
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
    void testPlaceMarketOrderFillsImmediatelyAtLatestPrice() {
        Account account = activeAccount();
        stubAccountAndInstrument(account, aapl());
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("150.123456"));
        when(positionRepository.findByAccountIdAndSymbol("12345", "AAPL")).thenReturn(Optional.empty());
        Order[] saved = new Order[1];
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });
        when(orderRepository.findById(any(UUID.class))).thenAnswer(invocation -> Optional.of(saved[0]));

        OrderResponse response = orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "MARKET", 10, null, "key-1"));

        assertEquals(OrderStatus.FILLED, response.getStatus());
        assertEquals(new BigDecimal("150.12"), response.getPrice());
        assertEquals(new BigDecimal("498.80"), account.getCashBalance());
        verify(positionRepository).save(any(Position.class));
    }

    @Test
    void testPlaceMarketOrderFailsWithoutPriceData() {
        stubAccountAndInstrument(activeAccount(), aapl());
        when(priceService.getCurrentPrice("AAPL")).thenThrow(new PriceNotFoundException("No price data"));

        assertThrows(PriceNotFoundException.class, () -> orderService.placeOrder(new PlaceOrderRequest(
            "12345", "AAPL", "BUY", "MARKET", 10, null, "key-1")));
        verify(orderRepository, never()).save(any());
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
}
