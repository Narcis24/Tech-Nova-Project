package com.neueda.app.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.neueda.app.enums.OrderSide;

/**
 * Event published by the execution-engine when an order has been filled.
 * Contains the execution details including the actual fill price.
 */
public record ExecutionEvent(
        UUID executionId,
        UUID orderId,
        String accountId,
        String symbol,
        OrderSide side,
        int quantity,
        BigDecimal price,
        BigDecimal limitPrice,
        String venue,
        Instant executedOn
) {
}
