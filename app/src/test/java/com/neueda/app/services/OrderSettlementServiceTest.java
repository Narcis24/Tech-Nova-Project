package com.neueda.app.services;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.neueda.app.dtos.OrderExecutedEvent;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.events.EventEnvelope;
import com.neueda.app.exceptions.OrderNotFoundException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderSettlementServiceTest {

    private static final BigDecimal PRICE = new BigDecimal("150.00");

    private OrderRepository orderRepository;
    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private InstrumentRepository instrumentRepository;
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private OrderSettlementService service;
    private UUID orderId;
    private OrderExecutedEvent event;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        accountRepository = mock(AccountRepository.class);
        positionRepository = mock(PositionRepository.class);
        instrumentRepository = mock(InstrumentRepository.class);
        service = new OrderSettlementService(orderRepository, accountRepository,
                positionRepository, instrumentRepository, mapper);
        orderId = UUID.randomUUID();
        event = new OrderExecutedEvent(orderId, "ACC1", "AAPL", "BUY", 2, PRICE, new BigDecimal("300.00"));
    }

    private void settle() throws Exception {
        EventEnvelope<OrderExecutedEvent> envelope = new EventEnvelope<>(
                "e1", "ORDER_EXECUTED", Instant.now(), "test", 1, event);
        service.processKafkaMessage(mapper.writeValueAsString(envelope));
    }

    @Test
    void duplicateFillStopsAtTheGuardWithoutTouchingCashOrPositions() throws Exception {
        when(orderRepository.transition(orderId, OrderStatus.PENDING, OrderStatus.FILLED, PRICE)).thenReturn(0);
        when(orderRepository.existsById(orderId)).thenReturn(true);

        settle();

        verifyNoInteractions(accountRepository, positionRepository, instrumentRepository);
    }

    @Test
    void unknownOrderIsAnError() {
        when(orderRepository.transition(orderId, OrderStatus.PENDING, OrderStatus.FILLED, PRICE)).thenReturn(0);
        when(orderRepository.existsById(orderId)).thenReturn(false);

        Exception e = assertThrows(Exception.class, this::settle);
        assertInstanceOf(OrderNotFoundException.class, e.getCause());
        verifyNoInteractions(accountRepository, positionRepository);
    }

    @Test
    void firstFillMovesCashAndPositionAfterTheGuard() throws Exception {
        Account account = new Account("ACC1", "Test", new BigDecimal("1000.00"),
                AccountStatus.ACTIVE, LocalDateTime.now());
        when(orderRepository.transition(orderId, OrderStatus.PENDING, OrderStatus.FILLED, PRICE)).thenReturn(1);
        when(accountRepository.findByIdForUpdate("ACC1")).thenReturn(Optional.of(account));
        when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.of(mock(Instrument.class)));
        when(positionRepository.findByAccountIdAndSymbol(any(), any())).thenReturn(Optional.empty());

        settle();

        assertEquals(new BigDecimal("700.00"), account.getCashBalance());
        verify(positionRepository).save(any());
    }

    @Test
    void rejectionResolvesAPendingOrderWithTheReason() throws Exception {
        when(orderRepository.reject(orderId, OrderStatus.PENDING, OrderStatus.REJECTED, "No fresh quote"))
                .thenReturn(1);
        com.neueda.app.dtos.OrderRejectedEvent rejected =
                new com.neueda.app.dtos.OrderRejectedEvent(orderId, "ACC1", "AAPL", "No fresh quote");

        service.processKafkaMessage(mapper.writeValueAsString(
                new EventEnvelope<>("e2", "ORDER_REJECTED", Instant.now(), "test", 1, rejected)));

        verify(orderRepository).reject(orderId, OrderStatus.PENDING, OrderStatus.REJECTED, "No fresh quote");
        verifyNoInteractions(accountRepository, positionRepository);
    }

    @Test
    void sellCreditsCashAndReducesThePosition() throws Exception {
        event = new OrderExecutedEvent(orderId, "ACC1", "AAPL", "SELL", 2, PRICE, new BigDecimal("300.00"));
        Account account = new Account("ACC1", "Test", new BigDecimal("1000.00"),
                AccountStatus.ACTIVE, LocalDateTime.now());
        com.neueda.app.models.Position position = mock(com.neueda.app.models.Position.class);
        when(orderRepository.transition(orderId, OrderStatus.PENDING, OrderStatus.FILLED, PRICE)).thenReturn(1);
        when(accountRepository.findByIdForUpdate("ACC1")).thenReturn(Optional.of(account));
        when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.of(mock(Instrument.class)));
        when(positionRepository.findByAccountIdAndSymbol(any(), any())).thenReturn(Optional.of(position));

        settle();

        assertEquals(new BigDecimal("1300.00"), account.getCashBalance());
        verify(position).updateOnSell(2);
    }

    @Test
    void fillThatCashCannotCoverIsRejectedNotRetried() throws Exception {
        Account poor = new Account("ACC1", "Test", new BigDecimal("100.00"),
                AccountStatus.ACTIVE, LocalDateTime.now());
        when(orderRepository.transition(orderId, OrderStatus.PENDING, OrderStatus.FILLED, PRICE)).thenReturn(1);
        when(accountRepository.findByIdForUpdate("ACC1")).thenReturn(Optional.of(poor));
        when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.of(mock(Instrument.class)));

        settle();

        assertEquals(new BigDecimal("100.00"), poor.getCashBalance());
        verify(orderRepository).reject(eq(orderId), eq(OrderStatus.FILLED), eq(OrderStatus.REJECTED), any());
        verify(positionRepository, never()).save(any());
    }
}
