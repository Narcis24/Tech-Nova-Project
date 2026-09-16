package com.neueda.app.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;


public class PositionResponseTest {
    
    @Test 
    public void positionResponseBasicTest() {
        String accountID = "12345L";
        String symbol = "AAPL";
        int quantity = 100;
        BigDecimal price = new BigDecimal("150.75");

        PositionResponse response = new PositionResponse();
        response.setAccountID(accountID);
        response.setSymbol(symbol);
        response.setQuantity(quantity);
        response.setPrice(price);

        assertEquals(accountID, response.getAccountID());
        assertEquals(symbol, response.getSymbol());
        assertEquals(quantity, response.getQuantity());
        assertEquals(price, response.getPrice());
    }

}
