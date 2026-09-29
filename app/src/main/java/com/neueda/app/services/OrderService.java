package com.neueda.app.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neueda.app.dtos.OrderPlacedEvent;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderType;
import com.neueda.app.exceptions.*;

import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.models.Position;

import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import com.neueda.app.dtos.OrderExecutedEvent;
import com.neueda.app.events.EventEnvelope;

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


public OrderService(OrderRepository orderRepository,
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

    public OrderResponse placeOrder(PlaceOrderRequest request) {

        // Validate input parameters first (before any DB lookups)
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
        // For MARKET orders, price is null (will be set during execution by ExecutionEngine)
        // This enables asynchronous execution: OrderService publishes to Kafka immediately,
        // ExecutionEngine fetches live market price and determines actual execution price later
        BigDecimal price = orderType == OrderType.LIMIT
            ? request.getPrice()
            : null;

        // Create Order entity with Account and Instrument objects
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
        
        // Publish ORDER_PLACED event to Kafka (asynchronous execution will happen in ExecutionEngine)
        OrderPlacedEvent event = new OrderPlacedEvent(
            order.getId(),
            order.getAccountId(),
            order.getSymbol(),
            order.getSide().toString(),
            order.getOrderType().toString(),
            order.getQuantity(),
            order.getPrice(),  // null for MARKET, limit price for LIMIT
            order.getIdempotencyKey()
        );

        log.info("Publishing ORDER_PLACED event: orderId={}, orderType={}, symbol={}", 
            order.getId(), orderType, order.getSymbol());

        eventProducerService.publishEvent(
            "trades",
            order.getId().toString(),
            "ORDER_PLACED",
            "OrderService",
            event
        );

        return new OrderResponse(order);
    }

    public OrderResponse executeOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        
        // Fails fast if the order is not PENDING, before any cash/position changes
        order.execute();

        // A limit order only fills once the market has reached its limit
        if (order.getOrderType() == OrderType.LIMIT) {
            BigDecimal marketPrice = priceService.getCurrentPrice(order.getSymbol());
            if (!order.isTriggeredBy(marketPrice)) {
                throw new OrderNotTriggeredException(
                    "Limit " + order.getPrice() + " not reached, market price is " + marketPrice);
            }
        }

        BigDecimal totalValue = order.getTotalValue();
        Account account = accountRepository.findById(order.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        Instrument instrument = instrumentRepository.findBySymbol(order.getSymbol())
            .orElseThrow(() -> new InstrumentNotFoundException("Instrument not found"));
        
        if (order.getSide() == OrderSide.BUY) {
            // BUY FLOW
            account.debitCash(totalValue);
            
            Position position = positionRepository
                .findByAccountIdAndSymbol(order.getAccountId(), order.getSymbol())
                .orElse(new Position(account, instrument, 0, BigDecimal.ZERO));
            
            position.updateOnBuy(order.getQuantity(), order.getPrice());
            
            accountRepository.save(account);
            positionRepository.save(position);
            
        } else {
            // SELL FLOW
            Position position = positionRepository
                .findByAccountIdAndSymbol(order.getAccountId(), order.getSymbol())
                .orElseThrow(() -> new InsufficientHoldingsException(
                    "No position in " + order.getSymbol() + " for account " + order.getAccountId()));
            
            position.updateOnSell(order.getQuantity());
            
            account.creditCash(totalValue);
            
            positionRepository.save(position);
            accountRepository.save(account);
        }
        
        orderRepository.save(order);
        return new OrderResponse(order);
    }


    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        
        order.cancel();  // Entity handles state validation
        orderRepository.save(order);
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

    /**
     * Kafka listener for ORDER_EXECUTED events from ExecutionEngine.
     * Performs database updates: order status to FILLED, positions, and cash.
     */
    @KafkaListener(topics = "tradeEvents", groupId = "order-service")
    public void handleOrderExecuted(EventEnvelope<OrderExecutedEvent> envelope) {
        log.info("Received ORDER_EXECUTED event: orderId={}", envelope.payload().getOrderId());
        
        OrderExecutedEvent event = envelope.payload();
        UUID orderId = event.getOrderId();
        
        try {
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
            
            // Mark order as FILLED
            order.execute();
            
            // Store the execution price
            order.setExecutionPrice(event.getExecutionPrice());
            
            // Retrieve account and instrument
            Account account = accountRepository.findById(event.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
            Instrument instrument = instrumentRepository.findBySymbol(event.getSymbol())
                .orElseThrow(() -> new InstrumentNotFoundException("Instrument not found"));
            
            // Update positions and cash based on order side
            if (event.getSide().equals("BUY")) {
                // BUY FLOW
                account.debitCash(event.getTotalValue());
                
                Position position = positionRepository
                    .findByAccountIdAndSymbol(event.getAccountId(), event.getSymbol())
                    .orElse(new Position(account, instrument, 0, BigDecimal.ZERO));
                
                position.updateOnBuy(event.getQuantity(), event.getExecutionPrice());
                
                accountRepository.save(account);
                positionRepository.save(position);
                
            } else {
                // SELL FLOW
                Position position = positionRepository
                    .findByAccountIdAndSymbol(event.getAccountId(), event.getSymbol())
                    .orElseThrow(() -> new InsufficientHoldingsException(
                        "No position in " + event.getSymbol() + " for account " + event.getAccountId()));
                
                position.updateOnSell(event.getQuantity());
                
                account.creditCash(event.getTotalValue());
                
                positionRepository.save(position);
                accountRepository.save(account);
            }
            
            // Save the order with updated status and execution price
            orderRepository.save(order);
            
            log.info("Order execution processed: orderId={}, status=FILLED, executionPrice={}", 
                orderId, event.getExecutionPrice());
            
        } catch (Exception e) {
            log.error("Error processing order execution: orderId={}", orderId, e);
            throw new RuntimeException("Failed to process order execution", e);
        }
    }
}
