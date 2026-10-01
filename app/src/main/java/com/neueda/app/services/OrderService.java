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
    private final OrderSettlementService orderSettlementService;


    public OrderService(
            OrderRepository orderRepository,
            AccountRepository accountRepository,
            PositionRepository positionRepository,
            InstrumentRepository instrumentRepository,
            PriceService priceService,
            EventProducerService eventProducerService,
            OrderSettlementService orderSettlementService) {

        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
        this.priceService = priceService;
        this.eventProducerService = eventProducerService;
        this.orderSettlementService = orderSettlementService;
    }

    private Order getOrderOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(OrderNotFoundException.MESSAGE + orderId));
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
            "order-request",
            order.getId().toString(),
            "ORDER_PLACED",
            "OrderService",
            event
        );

        return new OrderResponse(order);
    }

    public OrderResponse executeOrder(UUID orderId) {
        Order order = getOrderOrThrow(orderId);
        
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
        Order order = getOrderOrThrow(orderId);
        
        order.cancel();  // Entity handles state validation
        orderRepository.save(order);
        return new OrderResponse(order);
    }
    
    public OrderResponse rejectOrder(UUID orderId, String reason) {
        Order order = getOrderOrThrow(orderId);

        order.reject(reason);  // Entity handles state validation
        orderRepository.save(order);
        return new OrderResponse(order);
    }

    public OrderResponse getOrder(UUID orderId) {
        Order order = getOrderOrThrow(orderId);
        return new OrderResponse(order);
    }

    /**
     * Kafka listener for ORDER_EXECUTED events from ExecutionEngine.
     * Performs database updates: order status to FILLED, positions, and cash.
     */
    @KafkaListener(
        topics = "order-execution",
        groupId = "order-service"
    )
    public void handleOrderExecuted(String message) {
        log.info("Received message from order-execution topic");
        orderSettlementService.processKafkaMessage(message);
    }
}
