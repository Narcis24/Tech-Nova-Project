package com.neueda.app.dtos;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
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
