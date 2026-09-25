package com.neueda.app.models;

import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.contracts.OrderOperations;
import com.neueda.app.exceptions.InvalidOrderStateException;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Transient;
import java.lang.IllegalArgumentException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import lombok.NoArgsConstructor;
import lombok.Getter;

@Entity
@Table(name = "orders")
@NoArgsConstructor
@Getter
public class Order implements OrderOperations {

    /**IMMUTABLE*/
    @Id
    private UUID id;
    
    @Column(name = "account_id")
    private String accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, insertable = false, updatable = false)
    private Account account;
    
    @Column(name = "symbol", insertable = false, updatable = false)
    private String symbol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", nullable = false)
    private Instrument instrument;
    
    @Column(name = "side")
    @Enumerated(EnumType.STRING)
    private OrderSide side;
    
    @Column(name = "order_type")
    @Enumerated(EnumType.STRING)
    private OrderType orderType;
    
    @Column(name = "quantity")
    private int quantity;
    
    /** The limit price for LIMIT orders, the price the order was filled at for MARKET orders. */
    @Column(name = "price")
    private BigDecimal price;
    
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    
    @Column(name = "created_on")
    private LocalDateTime createdOn;
    
    /**MUTABLE*/
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    
    @Transient
    private LocalDateTime lastModified = LocalDateTime.now();
    
    @Column(name = "rejection_reason")
    private String rejectionReason;

    public Order(UUID id, Account account, Instrument instrument, OrderSide side, 
                 OrderType orderType, int quantity, BigDecimal price, String idempotencyKey, 
                 LocalDateTime createdOn) {
        // Validation
        if (id == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }
        if (instrument == null) {
            throw new IllegalArgumentException("Instrument cannot be null");
        }
        if (side == null) {
            throw new IllegalArgumentException("Side cannot be null");
        }
        if (orderType == null) {
            throw new IllegalArgumentException("Order type cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be > 0");
        }
        if (price == null) {
            throw new IllegalArgumentException("Price cannot be null");
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
        this.account = account;
        this.accountId = account.getAccountId();
        this.instrument = instrument;
        this.symbol = instrument.getSymbol();
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        // Same scale as the orders.price column, so cash and cost match what is stored
        this.price = price.setScale(2, RoundingMode.HALF_UP);
        this.idempotencyKey = idempotencyKey;
        this.createdOn = createdOn;
        this.status = OrderStatus.PENDING;       // Always starts as PENDING
        this.lastModified = createdOn;
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
     * Whether a fill is due at the given market price: always for MARKET orders,
     * for LIMIT orders a BUY at or below its limit and a SELL at or above it.
     */
    @Override
    public boolean isTriggeredBy(BigDecimal marketPrice) {
        if (orderType == OrderType.MARKET) {
            return true;
        }
        int comparison = marketPrice.compareTo(price);
        return side == OrderSide.BUY ? comparison <= 0 : comparison >= 0;
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
            throw new InvalidOrderStateException(
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
                ", orderType=" + orderType +
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