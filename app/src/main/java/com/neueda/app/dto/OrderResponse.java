package com.neueda.app.dto;

import com.neueda.app.enums.OrderStatus;
import lombok.Data;

@Data
public class OrderResponse {
    // Output for order API

    private Long orderID;
    private OrderStatus status;
    private String message;
    private String symbol;
    private String side; 
    private int quantity;
    private double price;
}
