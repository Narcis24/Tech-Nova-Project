package com.neueda.app.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.List;

/**
 * What's happening right now, for the Live panel on the admin home page. Takes the card's filters
 * but not its period: it always covers today and the trading days just before. Times are New York.
 *
 * Example:
 * {
 *   "asOf": "2026-10-20T18:30:00Z",
 *   "ordersToday": 37,
 *   "ordersLastHour": 6,
 *   "hourly": [
 *     { "start": "2026-10-16T13:00:00Z", "orders": 3, "future": false },
 *     ... 7 market hours (9:00 to 15:00) for each of the last 3 trading days
 *     { "start": "2026-10-20T19:00:00Z", "orders": 0, "future": true }
 *   ],
 *   "topToday": [
 *     { "symbol": "NVDA", "name": "NVIDIA Corporation", "assetClass": "EQUITY", "orders": 7 }
 *     ... up to 3
 *   ]
 * }
 */
public record AdminLiveResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant asOf,
    // Since midnight in New York, whatever happened to them
    @Schema(requiredMode = RequiredMode.REQUIRED) long ordersToday,
    @Schema(requiredMode = RequiredMode.REQUIRED) long ordersLastHour,
    // Market hours of the last 3 trading days, oldest first; weekends are skipped
    @Schema(requiredMode = RequiredMode.REQUIRED) List<LiveHour> hourly,
    // Most orders first, at most 3
    @Schema(requiredMode = RequiredMode.REQUIRED) List<LiveInstrument> topToday
) {}
