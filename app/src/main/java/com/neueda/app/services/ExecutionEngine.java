package com.neueda.app.services;

import com.neueda.app.dtos.OrderExecutedEvent;
import com.neueda.app.dtos.OrderPlacedEvent;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderType;
import com.neueda.app.events.EventEnvelope;
import com.neueda.app.exceptions.*;
import com.neueda.app.models.Order;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.InstrumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class ExecutionEngine {
    
    private final OrderRepository orderRepository;
    private final InstrumentRepository instrumentRepository;
    private final PriceService priceService;
    private final EventProducerService eventProducerService;

    public ExecutionEngine(OrderRepository orderRepository,
                          InstrumentRepository instrumentRepository,
                          PriceService priceService,
                          EventProducerService eventProducerService) {
        this.orderRepository = orderRepository;
        this.instrumentRepository = instrumentRepository;
        this.priceService = priceService;
        this.eventProducerService = eventProducerService;
    }

    @KafkaListener(topics = "trades", groupId = "execution-engine")
    public void handleOrderPlaced(EventEnvelope<OrderPlacedEvent> envelope) {
        log.info("Received OrderPlacedEvent: orderId={}", envelope.payload().getOrderId());
        
        OrderPlacedEvent event = envelope.payload();
        UUID orderId = event.getOrderId();
        
        try {
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
            
            // Get current market price
            BigDecimal marketPrice = priceService.getCurrentPrice(event.getSymbol());
            
            // For LIMIT orders, check if triggered
            if (order.getOrderType() == OrderType.LIMIT) {
                if (!order.isTriggeredBy(marketPrice)) {
                    log.info("LIMIT order not triggered: orderId={}, limit={}, marketPrice={}",
                        orderId, event.getPrice(), marketPrice);
                    return;  // Don't execute yet
                }
            }
            
            // Order can execute: determine execution price
            BigDecimal executionPrice = marketPrice;
            BigDecimal totalValue = executionPrice.multiply(new BigDecimal(event.getQuantity()));
            
            log.info("Order triggered for execution: orderId={}, executionPrice={}", orderId, executionPrice);
            
            // Publish ORDER_EXECUTED event to executions topic
            // OrderService listener will handle database updates
            OrderExecutedEvent executedEvent = new OrderExecutedEvent(
                orderId,
                event.getAccountId(),
                event.getSymbol(),
                event.getSide(),
                event.getQuantity(),
                executionPrice,
                totalValue
            );
            
            eventProducerService.publishEvent(
                "tradeEvents",
                orderId.toString(),
                "ORDER_EXECUTED",
                "ExecutionEngine",
                executedEvent
            );
            
            log.info("ORDER_EXECUTED event published: orderId={}", orderId);
            
        } catch (OrderNotFoundException | InstrumentNotFoundException e) {
            log.error("Error processing order: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error executing order: orderId={}", orderId, e);
        }
    }
}
