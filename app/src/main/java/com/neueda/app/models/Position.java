package com.neueda.app.models;

import com.neueda.app.contracts.PositionOperations;
import com.neueda.app.exceptions.InsufficientHoldingsException;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Column;
import java.math.BigDecimal;
import java.math.RoundingMode;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import lombok.NoArgsConstructor;
import lombok.Getter;

@Entity
@Table(name = "positions")
@IdClass(PositionKey.class)
@NoArgsConstructor
@Getter
public class Position implements PositionOperations {
    @Id
    @Column(name="account_id")
    private String accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, insertable = false, updatable = false)
    private Account account;
    
    @Id
    @Column(name = "symbol")
    private String symbol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", nullable = false, insertable = false, updatable = false)
    private Instrument instrument;
    
    @Column(name = "quantity")
    private int quantity;
    
    @Column(name = "average_cost")
    private BigDecimal averageCost;

    public Position(Account account, Instrument instrument, int quantity, BigDecimal averageCost) {
        // Allow quantity = 0 for new positions that haven't been opened yet

        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }
        if (instrument == null) {
            throw new IllegalArgumentException("Instrument cannot be null");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        if (averageCost == null) {
            throw new IllegalArgumentException("Average Cost cannot be null");
        }
        if (quantity > 0 && averageCost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Average cost must be > 0 when quantity > 0");
        }
        
        this.account = account;
        this.accountId = account.getAccountId();
        this.instrument = instrument;
        this.symbol = instrument.getSymbol();
        this.quantity = quantity;
        this.averageCost = averageCost;
    }

    @Override
    public void updateOnBuy(int quantity, BigDecimal price) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be > 0");
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be > 0");
        }
        
        BigDecimal totalCostOld = averageCost.multiply(new BigDecimal(this.quantity));
        BigDecimal totalCostNew = price.multiply(new BigDecimal(quantity));
        BigDecimal combinedCost = totalCostOld.add(totalCostNew);
        int combinedQuantity = this.quantity + quantity;
        
        this.averageCost = combinedCost.divide(new BigDecimal(combinedQuantity), 2, RoundingMode.HALF_UP);
        this.quantity = combinedQuantity;
    }

    @Override
    public void updateOnSell(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be > 0");
        }
        if (quantity > this.quantity) {
            throw new InsufficientHoldingsException(
                "Cannot sell " + quantity + " shares. Only " + this.quantity + " held."
            );
        }
        
        this.quantity -= quantity;
    }

    @Override
    public BigDecimal getMarketValue(BigDecimal currentPrice) {
        if (currentPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Current price cannot be negative");
        }
        return currentPrice.multiply(new BigDecimal(quantity));
    }

    @Override
    public BigDecimal getUnrealizedPnL(BigDecimal currentPrice) {
        if (currentPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Current price cannot be negative");
        }
        BigDecimal priceDifference = currentPrice.subtract(averageCost);
        return priceDifference.multiply(new BigDecimal(quantity));
    }

    @Override
    public String toString() {
        return "Position{" +
                "accountId='" + accountId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", quantity=" + quantity +
                ", averageCost=" + averageCost +
                '}';
    }
}