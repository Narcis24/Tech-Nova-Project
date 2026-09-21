package com.neueda.app.dto;

import java.math.BigDecimal;
import java.util.UUID;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.model.Order;
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
    private int quantity;
    private BigDecimal price;
    private OrderStatus status;
    private String message;
    
    public OrderResponse(Order order) {
        this.orderId = order.getId();
        this.accountId = order.getAccountId();
        this.symbol = order.getSymbol();
        this.side = order.getSide().toString();
        this.quantity = order.getQuantity();
        this.price = order.getPrice();
        this.status = order.getStatus();
    }
}
