package com.neueda.events;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderPlacedEvent(
    UUID orderId,
    String accountId,
    String symbol,
    String side,           // BUY or SELL
    String orderType,      // MARKET or LIMIT
    Integer quantity,
    BigDecimal price,      // null for MARKET
    String idempotencyKey
) {
}
