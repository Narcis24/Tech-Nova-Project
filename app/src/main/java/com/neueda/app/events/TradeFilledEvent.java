package com.neueda.app.events;

import java.math.BigDecimal;
import java.util.UUID;

public record TradeFilledEvent(
    UUID orderId,
    UUID accountId,
    String symbol,
    String side,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal totalValue
) {}