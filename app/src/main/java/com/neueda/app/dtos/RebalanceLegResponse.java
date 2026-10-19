package com.neueda.app.dtos;

import com.neueda.app.enums.OrderStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One order of a rebalance run, with how it ended. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RebalanceLegResponse {
    private UUID orderId;
    private String phase;
    private String symbol;
    private String side;
    private int quantity;
    private OrderStatus status;
    private String failureReason;
}
