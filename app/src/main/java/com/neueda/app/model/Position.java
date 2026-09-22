package com.neueda.app.model;

import com.neueda.app.contract.PositionOperations;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class Position implements PositionOperations {
    private final String accountId;
    private final String symbol;
    private int quantity;
    private BigDecimal averageCost;

    public Position(String accountId, String symbol, int quantity, BigDecimal averageCost) {
        // Allow quantity = 0 for new positions that haven't been opened yet

        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null");
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
        
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageCost = averageCost;
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
    public int getQuantity() {
        return quantity;
    }

    @Override
    public BigDecimal getAverageCost() {
        return averageCost;
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
            throw new IllegalArgumentException(
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