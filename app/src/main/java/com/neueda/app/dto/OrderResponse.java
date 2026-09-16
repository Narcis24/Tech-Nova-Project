package com.neueda.app.dto;

import java.math.BigDecimal;

import com.neueda.app.enums.OrderStatus;
import lombok.Data;

@Data
public class OrderResponse {

    private String orderID;
    private OrderStatus status;
    private String message;
    private String symbol;
    private String side; 
    private int quantity;
    private BigDecimal price;
}
