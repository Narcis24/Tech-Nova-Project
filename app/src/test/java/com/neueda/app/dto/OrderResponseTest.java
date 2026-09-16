package com.neueda.app.dto;

import com.neueda.app.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;


public class OrderResponseTest {
    
    @Test 
    public void orderResponseBasicTest() {
        Long orderID = 10001L;
        OrderStatus status = OrderStatus.PENDING;
        String message = "Order placed successfully";
        String symbol = "AAPL";
        String side = "BUY";
        int quantity = 100;
        BigDecimal price = new BigDecimal("150.75");

        OrderResponse response = new OrderResponse();
        response.setOrderID(orderID);
        response.setStatus(status);
        response.setMessage(message);
        response.setSymbol(symbol);
        response.setSide(side);
        response.setQuantity(quantity);
        response.setPrice(price);

        assertEquals(orderID, response.getOrderID());
        assertEquals(status, response.getStatus());
        assertEquals(message, response.getMessage());
        assertEquals(symbol, response.getSymbol());
        assertEquals(side, response.getSide());
        assertEquals(quantity, response.getQuantity());
        assertEquals(price, response.getPrice());
    }
}
