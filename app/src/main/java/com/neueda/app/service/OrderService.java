package com.neueda.app.service;

import com.neueda.app.dto.OrderResponse;
// import com.neueda.app.dto.PlaceOrderRequest;
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
import java.util.UUID;

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

    // public OrderResponse placeOrder(PlaceOrderRequest request) {
    /*
    public void placeOrder(Object request) {
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
        
        // Create Order entity
        Order order = new Order(
            UUID.randomUUID(),
            request.getAccountId(),
            request.getSymbol(),
            request.getSide(),
            request.getQuantity(),
            request.getPrice(),
            UUID.randomUUID().toString()
        );
        
        orderRepository.save(order);
        // return new OrderResponse(order); // DTO on another branch
    }
    */

     // public OrderResponse executeOrder(UUID orderId) {
     public OrderResponse executeOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new TradingException("Order not found: " + orderId));
        
        BigDecimal totalValue = order.getTotalValue();
        
        if (order.getSide() == OrderSide.BUY) {
            // BUY FLOW
            Account account = accountRepository.findById(order.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
            
            account.debitCash(totalValue);
            
            Position position = positionRepository
                .findByAccountIdAndSymbol(order.getAccountId(), order.getSymbol())
                .orElse(new Position(order.getAccountId(), order.getSymbol(), 0, BigDecimal.ZERO));
            
            position.updateOnBuy(order.getQuantity(), order.getPrice());
            
            accountRepository.update(account);
            if (position.getQuantity() == order.getQuantity()) {
                positionRepository.save(position);
            } else {
                positionRepository.update(position);
            }
            
        } else {
            // SELL FLOW
            Position position = positionRepository
                .findByAccountIdAndSymbol(order.getAccountId(), order.getSymbol())
                .orElseThrow(() -> new TradingException("Position not found"));
            
            position.updateOnSell(order.getQuantity());
            
            Account account = accountRepository.findById(order.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
            
            account.creditCash(totalValue);
            
            positionRepository.update(position);
            accountRepository.update(account);
        }
        
        order.execute();
        orderRepository.update(order);
        return new OrderResponse(order.getId(), order.getAccountId(), order.getSymbol(), 
                                  order.getSide(), order.getQuantity(), order.getPrice(), 
                                  order.getStatus(), order.getCreatedOn(), order.getLastModified());
    }


    // public OrderResponse cancelOrder(UUID orderId) {
    /*
    public void cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new TradingException("Order not found: " + orderId));
        
        order.cancel();  // Entity handles state validation
        orderRepository.update(order);
        // return new OrderResponse(order); // DTO on another branch
    }
    */
    
    // public OrderResponse getOrder(UUID orderId) {
    /*
    public void getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new TradingException("Order not found: " + orderId));
        // return new OrderResponse(order); // DTO on another branch
    }
    */
}
