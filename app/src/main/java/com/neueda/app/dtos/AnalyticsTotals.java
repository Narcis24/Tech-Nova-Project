package com.neueda.app.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.math.BigDecimal;

/** The four headline figures for a set of orders: a whole period, one chart point, or one slice of it. */
public record AnalyticsTotals(
    // Quantity × price of FILLED orders; nothing ever sets PARTIALLY_FILLED, so partial fills are not counted
    @Schema(requiredMode = RequiredMode.REQUIRED) BigDecimal tradedValue,
    // Every order placed, whatever happened to it
    @Schema(requiredMode = RequiredMode.REQUIRED) long orders,
    // FILLED out of orders that reached a final status, 0 to 1; null when none has, so it isn't shown as 0%
    BigDecimal fillRate,
    // Distinct account owners who placed an order; orders on unclaimed accounts have no owner and add none
    @Schema(requiredMode = RequiredMode.REQUIRED) long activeTraders
) {}
