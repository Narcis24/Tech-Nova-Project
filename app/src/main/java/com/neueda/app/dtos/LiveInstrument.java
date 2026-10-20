package com.neueda.app.dtos;

import com.neueda.app.enums.AssetClass;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** An instrument by how many orders were placed on it today, whatever happened to them. */
public record LiveInstrument(
    @Schema(requiredMode = RequiredMode.REQUIRED) String symbol,
    @Schema(requiredMode = RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = RequiredMode.REQUIRED) AssetClass assetClass,
    @Schema(requiredMode = RequiredMode.REQUIRED) long orders
) {}
