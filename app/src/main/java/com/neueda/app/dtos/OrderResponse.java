package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.enums.TimeInForce;
import com.neueda.app.models.Order;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private UUID orderId;
    private String accountId;
    private String symbol;
    private String side;
    private OrderType orderType;
    private int quantity;
    private BigDecimal price;
    private OrderStatus status;
    private String message;
    private Instant createdOn;       // when the order was placed, in UTC
    private TimeInForce timeInForce; // LIMIT orders only
    private Instant expiresAt;       // when a still-pending LIMIT order expires, in UTC
    
    public OrderResponse(Order order) {
        this.orderId = order.getId();
        this.accountId = order.getAccountId();
        this.symbol = order.getSymbol();
        this.side = order.getSide().toString();
        this.orderType = order.getOrderType();
        this.quantity = order.getQuantity();
        this.price = order.getPrice();
        this.status = order.getStatus();
        this.message = order.getRejectionReason();
        // Stored as UTC without a zone; an Instant tells clients it is UTC
        this.createdOn = order.getCreatedOn() == null ? null : order.getCreatedOn().toInstant(ZoneOffset.UTC);
        this.timeInForce = order.getTimeInForce();
        this.expiresAt = order.getExpiresAt() == null ? null : order.getExpiresAt().toInstant(ZoneOffset.UTC);
    }
}
