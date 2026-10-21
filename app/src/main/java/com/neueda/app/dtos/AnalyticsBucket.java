package com.neueda.app.dtos;

import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.Map;

/**
 * One point on the analytics chart: an hour, day or week. Empty buckets are included with zero
 * totals, so the chart has no gaps. The maps hold every asset class and side, even those with no orders.
 */
public record AnalyticsBucket(
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant start,
    // Today's hours that haven't happened yet; the chart draws them as empty slots
    @Schema(requiredMode = RequiredMode.REQUIRED) boolean future,
    @Schema(requiredMode = RequiredMode.REQUIRED) AnalyticsTotals totals,
    @Schema(requiredMode = RequiredMode.REQUIRED) Map<AssetClass, AnalyticsTotals> byAssetClass,
    @Schema(requiredMode = RequiredMode.REQUIRED) Map<OrderSide, AnalyticsTotals> bySide
) {}
