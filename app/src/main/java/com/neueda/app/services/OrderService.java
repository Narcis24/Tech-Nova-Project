package com.neueda.app.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neueda.events.EventTypes;
import com.neueda.events.OrderPlacedEvent;
import com.neueda.events.Topics;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.exceptions.*;

import com.neueda.app.models.Order;

import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.services.OrderValidator.ValidatedOrder;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class OrderService {
    
    private static final String ORDER_NOT_FOUND = "Order not found: ";

    private final OrderRepository orderRepository;
    private final OrderValidator orderValidator;
    private final EventProducerService eventProducerService;


    public OrderService(
            OrderRepository orderRepository,
            OrderValidator orderValidator,
            EventProducerService eventProducerService) {

        this.orderRepository = orderRepository;
        this.orderValidator = orderValidator;
        this.eventProducerService = eventProducerService;
    }

    public OrderResponse placeOrder(PlaceOrderRequest request) {

        // Validate input parameters first (before any DB lookups)
        orderValidator.checkInputs(request);

        // Check for duplicate idempotency key before looking up resources
        if (request.getIdempotencyKey() != null
                && orderRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            throw new DuplicateOrderException(
                "Order already submitted with idempotency key: " + request.getIdempotencyKey()
            );
        }

        // Account, instrument, price, and the cash or shares to cover it
        ValidatedOrder valid = orderValidator.validate(request);

        // Create Order entity with Account and Instrument objects
        Order order = new Order(
            UUID.randomUUID(),
            valid.account(),
            valid.instrument(),
            valid.side(),
            valid.orderType(),
            request.getQuantity(),
            valid.price(),
            request.getIdempotencyKey() != null ? request.getIdempotencyKey() : UUID.randomUUID().toString(),
            LocalDateTime.now(ZoneOffset.UTC)
        );

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
            order.getId(), order.getOrderType(), order.getSymbol());

        eventProducerService.publishEvent(
            Topics.ORDER_REQUEST,
            order.getId().toString(),
            EventTypes.ORDER_PLACED,
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
            .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND + orderId));
        if (updated == 0) {
            throw new InvalidOrderStateException(
                "Operation not allowed. Only PENDING orders can be modified. Order is " + order.getStatus());
        }
        return new OrderResponse(order);
    }
    
    public OrderResponse rejectOrder(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND + orderId));

        order.reject(reason);  // Entity handles state validation
        orderRepository.save(order);
        return new OrderResponse(order);
    }

    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND + orderId));
        return new OrderResponse(order);
    }
}
