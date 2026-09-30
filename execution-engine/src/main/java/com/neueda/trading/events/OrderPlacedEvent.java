package com.neueda.trading.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlacedEvent {
    private UUID orderId;
    private String accountId;
    private String symbol;
    private String side;           // BUY or SELL
    private String orderType;      // MARKET or LIMIT
    private Integer quantity;
    private BigDecimal price;
    private String idempotencyKey;
}