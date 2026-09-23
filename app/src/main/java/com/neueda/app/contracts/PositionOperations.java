package com.neueda.app.contracts;

import java.math.BigDecimal;

public interface PositionOperations {
    
    String getAccountId();
    
    String getSymbol();
    
    int getQuantity();
    
    BigDecimal getAverageCost();
    
    void updateOnBuy(int quantity, BigDecimal price);
    
    void updateOnSell(int quantity);
    
    BigDecimal getMarketValue(BigDecimal currentPrice);
    
    BigDecimal getUnrealizedPnL(BigDecimal currentPrice);
}
