package com.neueda.trading.events;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderExecutedEvent(
    UUID orderId,
    String accountId,
    String symbol,
    String side,
    int quantity,
    BigDecimal executionPrice,
    BigDecimal totalValue
) {
}