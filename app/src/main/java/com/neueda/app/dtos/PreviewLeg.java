package com.neueda.app.dtos;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PreviewLeg {
    private String symbol;
    private boolean inPie;
    private int currentQuantity;
    private BigDecimal price;
    private BigDecimal currentValue;
    private BigDecimal currentPct;
    private BigDecimal targetPct;
    private BigDecimal targetValue;
    /** The target value less the current value: what the account would buy (positive) or sell to reach it. */
    private BigDecimal difference;
    private String action;
    private int quantity;
    private BigDecimal estimatedValue;
}
