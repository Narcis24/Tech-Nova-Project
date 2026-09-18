package com.neueda.app.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderRequest {
    private String accountId;
    private String symbol;
    private String side;
    private Integer quantity;
    private BigDecimal price;
    private String idempotencyKey;
}
