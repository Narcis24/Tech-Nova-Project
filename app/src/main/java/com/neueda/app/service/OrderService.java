package com.neueda.app.service;

import org.springframework.stereotype.Service;
import com.neueda.app.dto.OrderResponse;
import com.neueda.app.dto.PlaceOrderRequest;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.exceptions.*;
import com.neueda.app.model.Account;
import com.neueda.app.model.Instrument;
import com.neueda.app.model.Order;
import com.neueda.app.model.Position;
import com.neueda.app.repository.AccountRepository;
import com.neueda.app.repository.InstrumentRepository;
import com.neueda.app.repository.OrderRepository;
import com.neueda.app.repository.PositionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
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
            .orElseThrow(() -> new TradingException("Order not found: " + orderId));
        
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
                .orElseThrow(() -> new TradingException("Position not found"));
            
            position.updateOnSell(order.getQuantity());
            
            account.creditCash(totalValue);
            
            positionRepository.save(position);
            accountRepository.save(account);
        }
        
        order.execute();
        orderRepository.save(order);
        return new OrderResponse(order);
    }


    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new TradingException("Order not found: " + orderId));
        
        order.cancel();  // Entity handles state validation
        orderRepository.save(order);
        return new OrderResponse(order);
    }
    
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new TradingException("Order not found: " + orderId));
        return new OrderResponse(order);
    }
}
