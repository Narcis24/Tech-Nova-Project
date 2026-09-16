package com.neueda.app.dto;

import java.math.BigInteger;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PlaceOrderRequest {

    @NotNull(message = "Account ID required")
    private Long accountId;

    @NotBlank(message = "Symbol required")
    private String symbol;

    @Pattern(regexp= "BUY|SELL")
    private String side;

    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @DecimalMin("0.01")
    private BigDecimal price;

    private String idempotency_key;    

}
