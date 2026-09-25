package com.neueda.app.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderRequest {
    @NotBlank(message = "Account ID is required")
    private String accountId;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotBlank(message = "Side must be BUY or SELL")
    private String side;

    @NotBlank(message = "Order type must be MARKET or LIMIT")
    private String orderType;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    /** Required for LIMIT orders and not allowed for MARKET orders, which OrderService enforces. */
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}
