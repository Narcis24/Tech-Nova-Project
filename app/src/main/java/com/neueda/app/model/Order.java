package com.neueda.app.model;

import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.contract.OrderOperations;
import java.lang.IllegalArgumentException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


public class Order implements OrderOperations {

    /**IMMUTABLE*/
    private final UUID id;                      
    private final String accountId;            
    private final String symbol;                
    private final OrderSide side;              
    private final int quantity;                 
    private final BigDecimal price;             
    private final String idempotencyKey;        
    private final LocalDateTime createdOn;      
    
    /**MUTABLE*/
    private OrderStatus status;                 
    private LocalDateTime lastModified; 
    private String rejectionReason;       

    public Order(UUID id, String accountId, String symbol, OrderSide side, 
                 int quantity, BigDecimal price, String idempotencyKey, 
                 LocalDateTime createdOn) {
        // Validation
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null");
        }
        if (side == null) {
            throw new IllegalArgumentException("Side cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be > 0");
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be > 0");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey cannot be null");
        }
        if (createdOn == null) {
            throw new IllegalArgumentException("Creation date cannot be null");
        }
        
        this.id = id;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.idempotencyKey = idempotencyKey;
        this.createdOn = createdOn;
        this.status = OrderStatus.PENDING;       // Always starts as PENDING
        this.lastModified = createdOn;
    }
    
    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getAccountId() {
        return accountId;
    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public OrderSide getSide() {
        return side;
    }

    @Override
    public int getQuantity() {
        return quantity;
    }

    @Override
    public BigDecimal getPrice() {
        return price;
    }

    @Override
    public OrderStatus getStatus() {
        return status;
    }

    @Override
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    //BUSINESS LOGIC

    /**
     * Calculates total order value (quantity * price).
     * Used for cash debit/credit.
     */
    @Override
    public BigDecimal getTotalValue() {
        return price.multiply(new BigDecimal(quantity));
    }

    /**
     * Executes the order: PENDING → FILLED
     */
    @Override
    public void execute() {
        
        requirePending();

        this.status = OrderStatus.FILLED;
        this.lastModified = LocalDateTime.now();
    }
    /**
     * Cancels the order: PENDING → CANCELLED
     * Can only cancel if still PENDING.
     */
    @Override
    public void cancel() {

        requirePending();
        
        this.status = OrderStatus.CANCELLED;
        this.lastModified = LocalDateTime.now();
    }

    /**
     * Rejects the order: PENDING → REJECTED
     */
    @Override
    public void reject(String reason) {

        requirePending();

        this.status = OrderStatus.REJECTED;
        this.lastModified = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    private void requirePending() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalArgumentException(
                "Operation not allowed. Only PENDING orders can be modified."
            );
        }
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", accountId='" + accountId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", quantity=" + quantity +
                ", price=" + price +
                ", status=" + status +
                ", totalValue=" + getTotalValue() +
                ", createdOn=" + createdOn +
                ", lastModified=" + lastModified +
                ", rejectionReason=" + rejectionReason +
                '}';
    }
}