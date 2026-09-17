package com.neueda.app.service;

import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.model.Account;
import com.neueda.app.model.Order;
import com.neueda.app.model.Position;
import com.neueda.app.repository.AccountRepository;
import com.neueda.app.repository.InstrumentRepository;
import com.neueda.app.repository.OrderRepository;
import com.neueda.app.repository.PositionRepository;

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

    private OrderService orderService;

    @BeforeEach
    void setUp() {

        orderRepository = mock(OrderRepository.class);
        accountRepository = mock(AccountRepository.class);
        positionRepository = mock(PositionRepository.class);
        instrumentRepository = mock(InstrumentRepository.class);

        orderService = new OrderService(
            orderRepository,
            accountRepository,
            positionRepository,
            instrumentRepository
        );
    }

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

        Order order = new Order(
            orderId,
            "12345",
            "AAPL",
            OrderSide.BUY,
            10,
            new BigDecimal("100.00"),
            UUID.randomUUID().toString()
        );

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        when(accountRepository.findById("12345"))
            .thenReturn(Optional.of(account));

        when(positionRepository.findByAccountIdAndSymbol("12345", "AAPL"))
            .thenReturn(Optional.empty());


        // Act
        OrderResponse response = orderService.executeOrder(orderId);


        // Assert
        assertNotNull(response);

        assertEquals(
            new BigDecimal("1000.00"),
            account.getCashBalance()
        );

        assertEquals(10, 
            positionRepository
                .findByAccountIdAndSymbol("12345", "AAPL")
                .orElse(new Position(
                    "12345",
                    "AAPL",
                    0,
                    BigDecimal.ZERO
                ))
                .getQuantity()
        );

        verify(accountRepository).update(account);
        verify(positionRepository).save(any(Position.class));
        verify(orderRepository).update(order);
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

        Order order = new Order(
            orderId,
            "12345",
            "AAPL",
            OrderSide.SELL,
            10,
            new BigDecimal("100.00"),
            UUID.randomUUID().toString()
        );

        Position position = new Position(
            "12345",
            "AAPL",
            20,
            new BigDecimal("80.00")
        );

        // When the service looks for the order
        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        // When the service looks for the position
        when(positionRepository.findByAccountIdAndSymbol("12345", "AAPL"))
            .thenReturn(Optional.of(position));

        // When the service looks for the account
        when(accountRepository.findById("12345"))
            .thenReturn(Optional.of(account));

        // Execute the SELL order
        OrderResponse response = orderService.executeOrder(orderId);

        // 10 shares × €100 = €1,000
        // Account should increase from €2,000 to €3,000
        assertEquals(
            new BigDecimal("3000.00"),
            account.getCashBalance()
        );

        // Position should decrease from 20 shares to 10
        assertEquals(10, position.getQuantity());

        // Order should now be executed
        assertEquals(OrderStatus.EXECUTED, order.getStatus());

        // Verify repositories were updated
        verify(positionRepository).update(position);
        verify(accountRepository).update(account);
        verify(orderRepository).update(order);
    }
}