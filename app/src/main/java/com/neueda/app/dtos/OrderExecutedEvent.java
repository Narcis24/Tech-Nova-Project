package com.neueda.app.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderExecutedEvent {
    private UUID orderId;
    private String accountId;
    private String symbol;
    private String side;           // BUY or SELL
    private Integer quantity;
    private BigDecimal executionPrice;
    private BigDecimal totalValue;
}
