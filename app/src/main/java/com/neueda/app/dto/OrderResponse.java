package com.neueda.app.dto;

import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class OrderResponse {
    private UUID id;
    private String accountId;
    private String symbol;
    private OrderSide side;
    private int quantity;
    private BigDecimal price;
    private OrderStatus status;
    private LocalDateTime createdOn;
    private LocalDateTime lastModified;
    private String rejectionReason;

    public OrderResponse(UUID id, String accountId, String symbol, OrderSide side, 
                        int quantity, BigDecimal price, OrderStatus status, 
                        LocalDateTime createdOn, LocalDateTime lastModified) {
        this.id = id;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.status = status;
        this.createdOn = createdOn;
        this.lastModified = lastModified;
    }

    // Getters
    public UUID getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedOn() { return createdOn; }
    public LocalDateTime getLastModified() { return lastModified; }
    public String getRejectionReason() { return rejectionReason; }
}
