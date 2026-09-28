package com.neueda.app.contracts;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDateTime;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;

public interface OrderOperations {
    // Getters
    UUID getId();

    String getAccountId();

    String getSymbol();

    OrderSide getSide();

    int getQuantity();

    BigDecimal getPrice();

    OrderType getOrderType();

    OrderStatus getStatus();

    String getIdempotencyKey();

    LocalDateTime getCreatedOn();
    
    // Business logic
    BigDecimal getTotalValue(); 

    boolean isTriggeredBy(BigDecimal marketPrice);

    void execute();   

    void cancel();  
                      
    void reject(String reason);       
}