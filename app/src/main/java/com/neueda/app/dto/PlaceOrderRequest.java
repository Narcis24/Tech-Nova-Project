package com.neueda.app.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class PlaceOrderRequest {

    private String accountID;
    private String symbol;
    private String side;
    private Integer quantity;
    private BigDecimal price;
    private String idempotency_key;    

}
