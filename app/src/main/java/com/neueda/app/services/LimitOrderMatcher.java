package com.neueda.app.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PriceRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * DEPRECATED: Limit order matching is now handled by the execution-engine.
 * 
 * Previously filled PENDING limit orders once the latest price reached their limit.
 * With the event-driven architecture, all orders (MARKET and LIMIT) are published
 * to the execution-engine, which handles the matching logic and publishes fills
 * back to the app via the order-executions topic.
 * 
 * This component is kept for reference but does nothing.
 */
@Slf4j
@Component
public class LimitOrderMatcher {

    private final OrderRepository orderRepository;
    private final PriceRepository priceRepository;

    public LimitOrderMatcher(OrderRepository orderRepository,
                             PriceRepository priceRepository) {
        this.orderRepository = orderRepository;
        this.priceRepository = priceRepository;
    }

    @Scheduled(fixedDelayString = "${orders.matcher.interval-ms}")
    public void matchPendingLimitOrders() {
        // Limit order matching is now handled by the execution-engine.
        // This method is kept for backward compatibility but is a no-op.
        log.debug("Limit order matching is handled by the execution-engine");
    }
}
