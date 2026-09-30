package com.neueda.trading.events;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderExecutedEvent(
    UUID orderId,
    UUID accountId,
    String symbol,
    String side,
    Integer quantity,
    BigDecimal executionPrice,
    BigDecimal totalValue
) {}
