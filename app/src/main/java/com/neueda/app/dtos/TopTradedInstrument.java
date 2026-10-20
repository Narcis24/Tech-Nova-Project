package com.neueda.app.dtos;

import com.neueda.app.enums.AssetClass;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.math.BigDecimal;

/**
 * An instrument ranked by how much TechNova traders traded it, not by how its price moved (that is the
 * market overview's gainers and losers). Sell value is tradedValue minus buyValue.
 */
public record TopTradedInstrument(
    @Schema(requiredMode = RequiredMode.REQUIRED) String symbol,
    @Schema(requiredMode = RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = RequiredMode.REQUIRED) AssetClass assetClass,
    // Filled orders only, matching tradedValue
    @Schema(requiredMode = RequiredMode.REQUIRED) long orders,
    @Schema(requiredMode = RequiredMode.REQUIRED) long traders,
    @Schema(requiredMode = RequiredMode.REQUIRED) BigDecimal tradedValue,
    @Schema(requiredMode = RequiredMode.REQUIRED) BigDecimal buyValue
) {}
