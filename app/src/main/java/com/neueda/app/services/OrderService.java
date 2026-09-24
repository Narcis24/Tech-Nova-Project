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
    
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return new OrderResponse(order);
    }
}
