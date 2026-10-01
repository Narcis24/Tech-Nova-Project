package com.neueda.app.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final PriceService priceService;

    public OrderService(OrderRepository orderRepository,
                       AccountRepository accountRepository,
                       PositionRepository positionRepository,
                       InstrumentRepository instrumentRepository,
                       PriceService priceService) {
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
        this.priceService = priceService;
    }

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
        
        // A MARKET order trades at the latest price, a LIMIT order at the price it was placed with
        BigDecimal price = orderType == OrderType.MARKET
            ? priceService.getCurrentPrice(instrument.getSymbol())
            : request.getPrice();

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
        if (orderType == OrderType.MARKET) {
            return executeOrder(order.getId());
        }
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
    @KafkaListener(
        topics = "tradeEvents",
        groupId = "order-service"
    )
    public void handleOrderExecuted(String message) {

        log.info(
            "Received message from tradeEvents: {}",
            message
        );

        EventEnvelope<?> envelope;

        try {
            envelope = objectMapper.readValue(
                message,
                EventEnvelope.class
            );
        } catch (Exception e) {
            log.error(
                "Failed to deserialize EventEnvelope: message={}",
                message,
                e
            );
            return;
        }

        if (!"ORDER_EXECUTED".equals(envelope.eventType())) {
            log.debug(
                "Ignoring event type: {}",
                envelope.eventType()
            );
            return;
        }

        OrderExecutedEvent event;

        try {
            event = objectMapper.convertValue(
                envelope.payload(),
                OrderExecutedEvent.class
            );
        } catch (Exception e) {
            log.error(
                "Failed to deserialize ORDER_EXECUTED payload: payload={}",
                envelope.payload(),
                e
            );
            return;
        }

        UUID orderId = event.getOrderId();

        log.info(
            "Received ORDER_EXECUTED event: orderId={}",
            orderId
        );

        try {

            Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                    new OrderNotFoundException(
                        "Order not found: " + orderId
                    )
                );


            order.execute();


            order.setExecutionPrice(
                event.getExecutionPrice()
            );

            Account account = accountRepository
                .findById(event.getAccountId())
                .orElseThrow(() ->
                    new AccountNotFoundException(
                        "Account not found: "
                            + event.getAccountId()
                    )
                );

            Instrument instrument = instrumentRepository
                .findBySymbol(event.getSymbol())
                .orElseThrow(() ->
                    new InstrumentNotFoundException(
                        "Instrument not found: "
                            + event.getSymbol()
                    )
                );

            if ("BUY".equalsIgnoreCase(event.getSide())) {

                account.debitCash(
                    event.getTotalValue()
                );

                Position position = positionRepository
                    .findByAccountIdAndSymbol(
                        event.getAccountId(),
                        event.getSymbol()
                    )
                    .orElse(
                        new Position(
                            account,
                            instrument,
                            0,
                            BigDecimal.ZERO
                        )
                    );

                position.updateOnBuy(
                    event.getQuantity(),
                    event.getExecutionPrice()
                );

                accountRepository.save(account);
                positionRepository.save(position);

                log.info(
                    "BUY execution processed: orderId={}, symbol={}, quantity={}, executionPrice={}, totalValue={}",
                    orderId,
                    event.getSymbol(),
                    event.getQuantity(),
                    event.getExecutionPrice(),
                    event.getTotalValue()
                );

            } else if ("SELL".equalsIgnoreCase(event.getSide())) {

                /*
                * Position must already exist.
                */
                Position position = positionRepository
                    .findByAccountIdAndSymbol(
                        event.getAccountId(),
                        event.getSymbol()
                    )
                    .orElseThrow(() ->
                        new InsufficientHoldingsException(
                            "No position in "
                                + event.getSymbol()
                                + " for account "
                                + event.getAccountId()
                        )
                    );

                position.updateOnSell(
                    event.getQuantity()
                );

                account.creditCash(
                    event.getTotalValue()
                );

                positionRepository.save(position);
                accountRepository.save(account);

                log.info(
                    "SELL execution processed: orderId={}, symbol={}, quantity={}, executionPrice={}, totalValue={}",
                    orderId,
                    event.getSymbol(),
                    event.getQuantity(),
                    event.getExecutionPrice(),
                    event.getTotalValue()
                );

            } else {

                throw new IllegalArgumentException(
                    "Unknown order side: "
                        + event.getSide()
                );
            }

            orderRepository.save(order);

            log.info(
                "Order execution processed successfully: orderId={}, status=FILLED, executionPrice={}",
                orderId,
                event.getExecutionPrice()
            );

        } catch (Exception e) {

            log.error(
                "Error processing ORDER_EXECUTED event: orderId={}",
                orderId,
                e
            );

            throw new RuntimeException(
                "Failed to process order execution: "
                    + orderId,
                e
            );
        }
    }
}
