package com.neueda.app.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.app.events.ExecutionEvent;
import com.neueda.app.services.OrderSettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes execution events from the execution-engine's order-executions topic.
 * Delegates to OrderSettlementService to update positions, cash balance, and order status.
 * 
 * At-least-once semantics: if processing fails, the order's offset won't be committed
 * and the message will be retried on next restart.
 */
@Component
public class ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ExecutionListener.class);

    private final OrderSettlementService orderSettlementService;
    private final ObjectMapper objectMapper;

    public ExecutionListener(OrderSettlementService orderSettlementService,
                             ObjectMapper objectMapper) {
        this.orderSettlementService = orderSettlementService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${kafka.topics.order-executions}",
            groupId = "${spring.application.name}",
            concurrency = "3"
    )
    public void onExecution(String message) {
        ExecutionEvent execution;
        try {
            execution = objectMapper.readValue(message, ExecutionEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse execution message: {}", message, e);
            // Don't rethrow - invalid messages should be skipped
            return;
        }

        try {
            log.info("Received execution for order {} from {}", execution.orderId(), execution.venue());
            orderSettlementService.processExecution(execution);
            log.info("Successfully settled order {} at {}", execution.orderId(), execution.price());
        } catch (Exception e) {
            log.error("Failed to process execution for order {}", execution.orderId(), e);
            // Rethrow to trigger retry on offset commit failure
            throw new RuntimeException("Failed to process execution", e);
        }
    }
}
