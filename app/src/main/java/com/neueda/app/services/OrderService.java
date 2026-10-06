package com.neueda.app.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neueda.app.dtos.OrderPlacedEvent;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.exceptions.*;

import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.exceptions.InsufficientHoldingsException;
import com.neueda.app.exceptions.InsufficientFundsException;
import com.neueda.app.models.Position;

import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final PriceService priceService;
    private final EventProducerService eventProducerService;


    public OrderService(
            OrderRepository orderRepository,
            AccountRepository accountRepository,
            PositionRepository positionRepository,
            InstrumentRepository instrumentRepository,
            PriceService priceService,
            EventProducerService eventProducerService) {

        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
        this.priceService = priceService;
        this.eventProducerService = eventProducerService;
    }
    
    /** Parses the order type without leaking the enum's class name on bad input. */
    private OrderType parseOrderType(String value) {
        try {
            return OrderType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("orderType must be one of " + Arrays.toString(OrderType.values()));
        }
    }

    /** Parses the order side without leaking the enum's class name on bad input. */
    private OrderSide parseOrderSide(String value) {
        try {
            return OrderSide.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("side must be one of " + Arrays.toString(OrderSide.values()));
        }
    }

    private void requireCanCover(Account account, Order order) {
        if (order.getSide() == OrderSide.BUY) {
            requireCash(account, order);
        } else {
            requireShares(order);
        }
    }

    private void requireCash(Account account, Order order) {
        if (account.getCashBalance().compareTo(order.getTotalValue()) < 0) {
            throw new InsufficientFundsException("Account " + account.getAccountId() + " has $"
                + account.getCashBalance() + " but order requires $" + order.getTotalValue());
        }
    }

    private void requireShares(Order order) {
        int held = positionRepository.findByAccountIdAndSymbol(order.getAccountId(), order.getSymbol())
            .map(Position::getQuantity).orElse(0);
        if (held < order.getQuantity()) {
            throw new InsufficientHoldingsException("Cannot sell " + order.getQuantity()
                + " shares of " + order.getSymbol() + ". Only " + held + " held.");
        }
    }

    public OrderResponse placeOrder(PlaceOrderRequest request) {

        // Validate input parameters first (before any DB lookups)
        if (request.getSide() == null || request.getOrderType() == null || request.getQuantity() == null) {
            throw new IllegalArgumentException("side, orderType and quantity are required");
        }
        OrderType orderType = parseOrderType(request.getOrderType());
        if (orderType == OrderType.LIMIT && request.getPrice() == null) {
            throw new IllegalArgumentException("price is required for LIMIT orders");
        }
        if (orderType == OrderType.MARKET && request.getPrice() != null) {
            throw new IllegalArgumentException("price must not be set for MARKET orders");
        }
        
        // Check for duplicate idempotency key before looking up resources
        if (request.getIdempotencyKey() != null
                && orderRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            throw new DuplicateOrderException(
                "Order already submitted with idempotency key: " + request.getIdempotencyKey()
            );
        }

        // Now look up resources after validation
        Account account = accountRepository.findById(request.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + request.getAccountId()
            ));
        account.validateStatus();
        
        Instrument instrument = instrumentRepository.findBySymbol(request.getSymbol())
            .orElseThrow(() -> new InstrumentNotFoundException(
                "Instrument not found: " + request.getSymbol()
            ));
        if (!instrument.isTradable()) {
            throw new TradingException("Instrument is not tradable: " + request.getSymbol());
        }
        
        // For LIMIT orders, use the price from the request
        // For MARKET orders, use the latest stored price as the reference price
        // (throws PriceNotFoundException if none). ExecutionEngine decides the actual fill price.
        BigDecimal price = orderType == OrderType.LIMIT
            ? request.getPrice()
            : priceService.getCurrentPrice(request.getSymbol());

        // Create Order entity with Account and Instrument objects
        Order order = new Order(
            UUID.randomUUID(),
            account,
            instrument,
            parseOrderSide(request.getSide()),
            orderType,
            request.getQuantity(),
            price,
            request.getIdempotencyKey() != null ? request.getIdempotencyKey() : UUID.randomUUID().toString(),
            LocalDateTime.now()
        );
        
        // Fail fast on what we can already see. Settlement re-checks at the real fill price.
        requireCanCover(account, order);

        orderRepository.save(order);
        
        // Publish ORDER_PLACED event to Kafka (asynchronous execution will happen in ExecutionEngine)
        OrderPlacedEvent event = new OrderPlacedEvent(
            order.getId(),
            order.getAccountId(),
            order.getSymbol(),
            order.getSide().toString(),
            order.getOrderType().toString(),
            order.getQuantity(),
            order.getPrice(),  // reference price for MARKET, limit price for LIMIT
            order.getIdempotencyKey()
        );

        log.info("Publishing ORDER_PLACED event: orderId={}, orderType={}, symbol={}", 
            order.getId(), orderType, order.getSymbol());

        eventProducerService.publishEvent(
            "order-request",
            order.getId().toString(),
            "ORDER_PLACED",
            "OrderService",
            event
        );

        return new OrderResponse(order);
    }

    public OrderResponse cancelOrder(UUID orderId) {
        // Guarded like settlement: only a PENDING order can move, so whichever of cancel and
        // fill reaches the row first wins and the other sees 0 rows, instead of overwriting it.
        int updated = orderRepository.changeStatus(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED);
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        if (updated == 0) {
            throw new InvalidOrderStateException(
                "Operation not allowed. Only PENDING orders can be modified. Order is " + order.getStatus());
        }
        return new OrderResponse(order);
    }
    
    public OrderResponse rejectOrder(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        order.reject(reason);  // Entity handles state validation
        orderRepository.save(order);
        return new OrderResponse(order);
    }

    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return new OrderResponse(order);
    }
}
