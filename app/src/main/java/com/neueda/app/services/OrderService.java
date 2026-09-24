package com.neueda.app.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.OrderSide;
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

    public OrderService(OrderRepository orderRepository,
                       AccountRepository accountRepository,
                       PositionRepository positionRepository,
                       InstrumentRepository instrumentRepository) {
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
    }

    public OrderResponse placeOrder(PlaceOrderRequest request) {
        if (request.getSide() == null || request.getQuantity() == null || request.getPrice() == null) {
            throw new IllegalArgumentException("side, quantity and price are required");
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
        
        // Create Order entity with Account and Instrument objects
        Order order = new Order(
            UUID.randomUUID(),
            account,
            instrument,
            OrderSide.valueOf(request.getSide().toUpperCase()),
            request.getQuantity(),
            request.getPrice(),
            request.getIdempotencyKey() != null ? request.getIdempotencyKey() : UUID.randomUUID().toString(),
            LocalDateTime.now()
        );
        
        orderRepository.save(order);
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
