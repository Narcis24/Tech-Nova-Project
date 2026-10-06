package com.neueda.trading.events;

import java.util.UUID;

public record OrderRejectedEvent(
    UUID orderId,
    String accountId,
    String symbol,
    String reason
) {
}
