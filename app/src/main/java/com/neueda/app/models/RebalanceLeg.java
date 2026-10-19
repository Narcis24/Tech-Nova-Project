package com.neueda.app.models;

import com.neueda.app.enums.OrderSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An order placed by a run. Its fill, rejection or cancellation is read from the order itself. */
@Entity
@Table(name = "rebalance_legs")
@NoArgsConstructor
@Getter
public class RebalanceLeg {
    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "symbol")
    private String symbol;

    @Column(name = "side")
    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Column(name = "quantity")
    private int quantity;

    public RebalanceLeg(UUID runId, UUID orderId, OrderSide side, String symbol, int quantity) {
        this.runId = runId;
        this.orderId = orderId;
        this.side = side;
        this.symbol = symbol;
        this.quantity = quantity;
    }
}
