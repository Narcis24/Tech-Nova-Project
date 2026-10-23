package com.neueda.app.dtos;

import com.neueda.app.enums.AssetClass;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.math.BigDecimal;

/** One asset class's part of the period's traded value. Every asset class is listed, even with none. */
public record AssetMixEntry(
    @Schema(requiredMode = RequiredMode.REQUIRED) AssetClass assetClass,
    @Schema(requiredMode = RequiredMode.REQUIRED) BigDecimal tradedValue,
    // Share of the period's traded value, 0 to 1; 0 for every class when nothing traded
    @Schema(requiredMode = RequiredMode.REQUIRED) BigDecimal share
) {}
