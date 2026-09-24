package com.neueda.app.services;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.exceptions.InvalidOrderStateException;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.exceptions.TradingException;
import com.neueda.app.models.Order;
import com.neueda.app.repositories.OrderRepository;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;

/**
 * Fills PENDING limit orders once the latest price reaches their limit.
 *
 * Deliberately not transactional: each fill (or rejection) runs in its own
 * transaction inside OrderService, so one failing order cannot roll back the others.
 */
@Slf4j
@Component
public class LimitOrderMatcher {

    private final OrderRepository orderRepository;
    private final PriceService priceService;
    private final OrderService orderService;

    public LimitOrderMatcher(OrderRepository orderRepository,
                             PriceService priceService,
                             OrderService orderService) {
        this.orderRepository = orderRepository;
        this.priceService = priceService;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${orders.matcher.interval-ms}")
    public void matchPendingLimitOrders() {
        for (Order order : orderRepository.findByStatusAndOrderType(OrderStatus.PENDING, OrderType.LIMIT)) {
            try {
                BigDecimal marketPrice = priceService.getCurrentPrice(order.getSymbol());
                if (order.isTriggeredBy(marketPrice)) {
                    orderService.executeOrder(order.getId());
                    log.info("Filled limit order {} at {}", order.getId(), order.getPrice());
                }
            } catch (PriceNotFoundException e) {
                log.warn("Skipping limit order {}: {}", order.getId(), e.getMessage());
            } catch (InvalidOrderStateException e) {
                log.info("Limit order {} was already handled", order.getId());
            } catch (OptimisticLockingFailureException e) {
                log.info("Limit order {} hit a concurrent update, will retry next run", order.getId());
            } catch (TradingException e) {
                // Triggered but cannot be filled (no funds, inactive account, ...)
                orderService.rejectOrder(order.getId(), e.getMessage());
                log.warn("Rejected limit order {}: {}", order.getId(), e.getMessage());
            } catch (RuntimeException e) {
                log.error("Unexpected error matching limit order {}", order.getId(), e);
            }
        }
    }
}
