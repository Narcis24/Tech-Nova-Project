package com.neueda.app.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.neueda.app.enums.OrderSide;

/**
 * Event published to Kafka when an order is created and ready for execution.
 * This mirrors the OrderEvent from the execution-engine; the two services
 * share the JSON contract, not a code dependency.
 */
public record OrderEvent(
        UUID orderId,
        String accountId,
        String symbol,
        OrderSide side,
        int quantity,
        BigDecimal price,
        Instant createdOn
) {
}
