package com.neueda.app.services;

import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.exceptions.InsufficientFundsException;
import com.neueda.app.exceptions.InvalidOrderStateException;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.repositories.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class LimitOrderMatcherTest {

    private OrderRepository orderRepository;
    private PriceService priceService;
    private OrderService orderService;
    private LimitOrderMatcher matcher;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        priceService = mock(PriceService.class);
        orderService = mock(OrderService.class);
        matcher = new LimitOrderMatcher(orderRepository, priceService, orderService);
    }

    private Order pendingLimit(OrderSide side, String limit) {
        Account account = new Account("12345", "Karl", "Devon", new BigDecimal("2000.00"),
            AccountStatus.ACTIVE, LocalDateTime.of(2026, 9, 17, 13, 0));
        Instrument instrument = new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
        Order order = new Order(UUID.randomUUID(), account, instrument, side, OrderType.LIMIT, 10,
            new BigDecimal(limit), "key", LocalDateTime.now());
        when(orderRepository.findByStatusAndOrderType(OrderStatus.PENDING, OrderType.LIMIT))
            .thenReturn(List.of(order));
        return order;
    }

    @Test
    void testFillsOrderWhenLimitIsReached() {
        Order order = pendingLimit(OrderSide.BUY, "100.00");
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("99.50"));

        matcher.matchPendingLimitOrders();

        verify(orderService).executeOrder(order.getId());
    }

    @Test
    void testLeavesOrderPendingWhenLimitIsNotReached() {
        pendingLimit(OrderSide.BUY, "100.00");
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.50"));

        matcher.matchPendingLimitOrders();

        verifyNoInteractions(orderService);
    }

    @Test
    void testSkipsOrderWithoutPriceData() {
        pendingLimit(OrderSide.BUY, "100.00");
        when(priceService.getCurrentPrice("AAPL")).thenThrow(new PriceNotFoundException("No price data"));

        matcher.matchPendingLimitOrders();

        verifyNoInteractions(orderService);
    }

    @Test
    void testRejectsTriggeredOrderThatCannotBeFilled() {
        Order order = pendingLimit(OrderSide.BUY, "100.00");
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("99.50"));
        when(orderService.executeOrder(order.getId()))
            .thenThrow(new InsufficientFundsException("Not enough cash"));

        matcher.matchPendingLimitOrders();

        verify(orderService).rejectOrder(order.getId(), "Not enough cash");
    }

    @Test
    void testDoesNotRejectOrderThatWasAlreadyHandled() {
        Order order = pendingLimit(OrderSide.BUY, "100.00");
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("99.50"));
        when(orderService.executeOrder(order.getId()))
            .thenThrow(new InvalidOrderStateException("Not pending"));

        matcher.matchPendingLimitOrders();

        verify(orderService, never()).rejectOrder(any(), any());
    }

    @Test
    void testDoesNotRejectOrderOnConcurrentUpdate() {
        Order order = pendingLimit(OrderSide.BUY, "100.00");
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("99.50"));
        when(orderService.executeOrder(order.getId()))
            .thenThrow(new OptimisticLockingFailureException("stale"));

        matcher.matchPendingLimitOrders();

        verify(orderService, never()).rejectOrder(any(), any());
    }
}
