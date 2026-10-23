package com.neueda.app.services;

import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.repositories.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class OrderExpiryJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T20:00:00Z"), ZoneOffset.UTC);

    private OrderRepository orderRepository;
    private OrderService orderService;
    private OrderExpiryJob job;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderService = mock(OrderService.class);
        job = new OrderExpiryJob(orderRepository, orderService, CLOCK);
    }

    @Test
    void expiresEveryPendingOrderWhoseTimeIsUp() {
        Order first = limitOrder();
        Order second = limitOrder();
        when(orderRepository.findByStatusAndExpiresAtLessThanEqual(OrderStatus.PENDING, LocalDateTime.of(2026, 10, 21, 20, 0)))
            .thenReturn(List.of(first, second));
        when(orderService.expireOrder(any())).thenReturn(true);

        job.expireDue();

        verify(orderService).expireOrder(first.getId());
        verify(orderService).expireOrder(second.getId());
    }

    @Test
    void oneFailureDoesNotStopTheRest() {
        Order failing = limitOrder();
        Order next = limitOrder();
        when(orderRepository.findByStatusAndExpiresAtLessThanEqual(eq(OrderStatus.PENDING), any()))
            .thenReturn(List.of(failing, next));
        when(orderService.expireOrder(failing.getId())).thenThrow(new IllegalStateException("Kafka down"));

        job.expireDue();

        verify(orderService).expireOrder(next.getId());
    }

    private static Order limitOrder() {
        Account account = new Account("ACC001", "Alice", new BigDecimal("1000.00"), AccountStatus.ACTIVE,
            LocalDateTime.of(2026, 10, 1, 0, 0));
        Instrument apple = new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
        return new Order(UUID.randomUUID(), account, apple, OrderSide.BUY, OrderType.LIMIT, 1,
            new BigDecimal("90.00"), UUID.randomUUID().toString(), LocalDateTime.of(2026, 10, 21, 14, 0));
    }
}
