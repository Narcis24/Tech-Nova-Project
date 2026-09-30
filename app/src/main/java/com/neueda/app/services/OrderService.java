package com.neueda.app.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.events.OrderEvent;
import com.neueda.app.exceptions.*;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.producers.OrderPublisher;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Manages order lifecycle: validation, creation, and publishing to the execution engine.
 * 
 * All orders (MARKET and LIMIT) are published to Kafka for async execution.
 * The execution-engine will fill orders and publish results to order-executions topic,
 * which OrderSettlementService will consume to update positions and cash.
 */
@Service
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final PriceService priceService;
    private final OrderPublisher orderPublisher;

    public OrderService(OrderRepository orderRepository,
                       AccountRepository accountRepository,
                       InstrumentRepository instrumentRepository,
                       PriceService priceService,
                       OrderPublisher orderPublisher) {
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.priceService = priceService;
        this.orderPublisher = orderPublisher;
    }

    /**
     * Places an order and publishes it to the execution engine.
     * 
     * For MARKET orders: uses the current market price for publishing
     * For LIMIT orders: uses the limit price provided
     * 
     * @param request the order request
     * @return the created order
     * @throws IllegalArgumentException if required fields are missing
     * @throws DuplicateOrderException if idempotency key already exists
     * @throws AccountNotFoundException if account doesn't exist
     * @throws InstrumentNotFoundException if instrument doesn't exist
     * @throws TradingException if instrument is not tradable
     */
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        if (request.getSide() == null || request.getOrderType() == null || request.getQuantity() == null) {
            throw new IllegalArgumentException("side, orderType and quantity are required");
        }
        OrderType orderType = OrderType.valueOf(request.getOrderType().toUpperCase());
        if (orderType == OrderType.LIMIT && request.getPrice() == null) {
            throw new IllegalArgumentException("price is required for LIMIT orders");
        }
        if (orderType == OrderType.MARKET && request.getPrice() != null) {
            throw new IllegalArgumentException("price must not be set for MARKET orders");
        }
        if (request.getIdempotencyKey() != null
                && orderRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            throw new DuplicateOrderException(
                "Order already submitted with idempotency key: " + request.getIdempotencyKey()
            );
        }

        // Validate account exists and is ACTIVE
        Account account = accountRepository.findById(request.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + request.getAccountId()
            ));
        account.validateStatus();
        
        // Validate instrument exists and is tradable
        Instrument instrument = instrumentRepository.findBySymbol(request.getSymbol())
            .orElseThrow(() -> new InstrumentNotFoundException(
                "Instrument not found: " + request.getSymbol()
            ));
        if (!instrument.isTradable()) {
            throw new TradingException("Instrument is not tradable: " + request.getSymbol());
        }
        
        // For MARKET orders, use current price; for LIMIT, use provided price
        BigDecimal price = orderType == OrderType.MARKET
            ? priceService.getCurrentPrice(instrument.getSymbol())
            : request.getPrice();

        // Create Order entity with PENDING status
        Order order = new Order(
            UUID.randomUUID(),
            account,
            instrument,
            OrderSide.valueOf(request.getSide().toUpperCase()),
            orderType,
            request.getQuantity(),
            price,
            request.getIdempotencyKey() != null ? request.getIdempotencyKey() : UUID.randomUUID().toString(),
            LocalDateTime.now()
        );
        
        orderRepository.save(order);
        
        // Publish order to execution engine
        try {
            publishOrder(order);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            // If publishing fails, we don't update status - order remains PENDING
            throw new TradingException("Failed to publish order to execution engine", e);
        }
        
        return new OrderResponse(order);
    }

    /**
     * Publishes an order to the execution engine via Kafka.
     * Updates order status to PUBLISHED after successful publish.
     */
    private void publishOrder(Order order) throws InterruptedException, ExecutionException, TimeoutException {
        OrderEvent event = new OrderEvent(
            order.getId(),
            order.getAccountId(),
            order.getSymbol(),
            order.getSide(),
            order.getQuantity(),
            order.getPrice(),
            Instant.now()
        );
        
        try {
            orderPublisher.publishOrder(event);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new TradingException("Failed to serialize order event", e);
        }
        
        // Only update status to PUBLISHED after Kafka acknowledges
        order.setStatus(OrderStatus.PUBLISHED);
        orderRepository.save(order);
    }

    /**
     * Cancels a pending order.
     * Can only cancel orders in PENDING or PUBLISHED status.
     */
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        
        // Only allow cancellation of PENDING or PUBLISHED orders
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PUBLISHED) {
            throw new InvalidOrderStateException(
                "Cannot cancel order in " + order.getStatus() + " status"
            );
        }
        
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        return new OrderResponse(order);
    }
    
    /**
     * Rejects a pending order with a reason.
     */
    public OrderResponse rejectOrder(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PUBLISHED) {
            throw new InvalidOrderStateException(
                "Cannot reject order in " + order.getStatus() + " status"
            );
        }

        order.setStatus(OrderStatus.REJECTED);
        order.setRejectionReason(reason);
        orderRepository.save(order);
        return new OrderResponse(order);
    }

    /**
     * Retrieves an order by ID.
     */
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return new OrderResponse(order);
    }
}
