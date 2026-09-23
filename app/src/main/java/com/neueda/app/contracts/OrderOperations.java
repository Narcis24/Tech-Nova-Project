package com.neueda.app.contracts;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDateTime;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;

public interface OrderOperations {
    // Getters
    UUID getId();

    String getAccountId();

    String getSymbol();

    OrderSide getSide();

    int getQuantity();

    BigDecimal getPrice();

    OrderStatus getStatus();

    String getIdempotencyKey();

    LocalDateTime getCreatedOn();
    
    // Business logic
    BigDecimal getTotalValue(); 

    void execute();   

    void cancel();  
                      
    void reject(String reason);       
}