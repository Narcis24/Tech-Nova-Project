package com.neueda.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PlaceOrderRequest {

    @NotNull(message = "Account ID required")
    private Long accountID;

    @NotBlank(message = "Symbol required")
    private String symbol;

    @NotNull(message = "Must be BUY or SELL")
    @Pattern(regexp= "BUY|SELL", message = "Must be BUY or SELL")
    private String side;

    @NotNull(message = "Quantity must be positive")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @NotNull(message = "Minimal price is 0.01")
    @DecimalMin(value = "0.01", message = "Minimal price is 0.01")
    private BigDecimal price;

    private String idempotency_key;    

}
