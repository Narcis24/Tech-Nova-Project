package com.neueda.app.dto;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PlaceOrderRequestTest {

    @Test
    public void placeOrderRequestGettersSettersTest() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountID("1234L");
        request.setSymbol("AAPL");
        request.setSide("BUY");
        request.setQuantity(100);
        request.setPrice(new BigDecimal("150.50"));
        request.setIdempotency_key("key-123");

        assertEquals("1234L", request.getAccountID());
        assertEquals("AAPL", request.getSymbol());
        assertEquals("BUY", request.getSide());
        assertEquals(100, request.getQuantity());
        assertEquals(new BigDecimal("150.50"), request.getPrice());
        assertEquals("key-123", request.getIdempotency_key());
    }
}
