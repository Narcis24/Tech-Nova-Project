package com.neueda.leap.dto;

import java.math.BigInteger;

import javax.print.DocFlavor.STRING;

public class PlaceOrderRequest {

    // Implement Logic for order request
    // @NotNull & @Positive
    
    // IV = Instance Variable
    // Following IV is chosen from ETP PDF

    private BigInteger accountId;
    private String symbol;
    private String side;
    private Integer quantity;
    private Double price;
    private String idempotencyKey;

}
