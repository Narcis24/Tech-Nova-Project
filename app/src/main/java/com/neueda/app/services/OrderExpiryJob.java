package com.neueda.app.services;

import com.neueda.app.enums.OrderStatus;
import com.neueda.app.models.Order;
import com.neueda.app.repositories.OrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires LIMIT orders still pending past their time in force, so they stop locking cash or shares.
 * Each order expires in its own transaction: one failure is logged and retried on the next run.
 */
@Component
@Slf4j
public class OrderExpiryJob {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final Clock clock;

    public OrderExpiryJob(OrderRepository orderRepository, OrderService orderService, Clock clock) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.orders.expiry-delay-ms:60000}")
    public void expireDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Order order : orderRepository.findByStatusAndExpiresAtLessThanEqual(OrderStatus.PENDING, now)) {
            try {
                if (orderService.expireOrder(order.getId())) {
                    log.info("Order expired: orderId={}, timeInForce={}, expiresAt={}",
                        order.getId(), order.getTimeInForce(), order.getExpiresAt());
                }
            } catch (RuntimeException e) {
                log.error("Could not expire order {}", order.getId(), e);
            }
        }
    }
}
