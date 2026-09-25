package com.neueda.app.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * DTO for placing trading orders
 * 
 * Validation Rules:
 * - quantity: 1 to 100,000 shares
 * - symbol: 3-5 characters (e.g., AAPL, MSFT)
 * - price: Must be positive (checked at controller level against market price)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderRequest {
    @NotBlank(message = "Account ID is required")
    private String accountId;

    @NotBlank(message = "Symbol is required")
    @Size(min = 3, max = 5, message = "Symbol must be between 3 and 5 characters")
    private String symbol;

    @NotBlank(message = "Side must be BUY or SELL")
    private String side;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 100000, message = "Quantity cannot exceed 100,000")
    private Integer quantity;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}
