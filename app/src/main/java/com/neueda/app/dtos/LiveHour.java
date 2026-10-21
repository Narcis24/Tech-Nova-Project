package com.neueda.app.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;

/** One market hour on the Live panel's bars: how many orders were placed in it. */
public record LiveHour(
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant start,
    @Schema(requiredMode = RequiredMode.REQUIRED) long orders,
    // Today's hours that haven't started yet; drawn as empty slots
    @Schema(requiredMode = RequiredMode.REQUIRED) boolean future
) {}
