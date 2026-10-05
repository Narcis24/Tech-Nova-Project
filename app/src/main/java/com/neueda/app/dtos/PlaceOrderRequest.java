package com.neueda.app.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * DTO for placing trading orders
 * 
 * Validation Rules:
 * - quantity: 1 to 100,000 shares
 * - symbol: 1-10 characters (e.g., V, AAPL, BRK-B)
 * - price: Must be positive when given (null is allowed for MARKET orders)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderRequest {
    @NotBlank(message = "Account ID is required")
    private String accountId;

    @NotBlank(message = "Symbol is required")
    @Size(min = 1, max = 10, message = "Symbol must be between 1 and 10 characters")
    private String symbol;

    @NotBlank(message = "Side must be one of [BUY, SELL]")
    @Pattern(regexp = "(?i)BUY|SELL", message = "Side must be one of [BUY, SELL]")
    private String side;

    @NotBlank(message = "Order type must be one of [MARKET, LIMIT]")
    @Pattern(regexp = "(?i)MARKET|LIMIT", message = "Order type must be one of [MARKET, LIMIT]")
    private String orderType;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 100000, message = "Quantity cannot exceed 100,000")
    private Integer quantity;

    /** Required for LIMIT orders and not allowed for MARKET orders, which OrderService enforces. */
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}
