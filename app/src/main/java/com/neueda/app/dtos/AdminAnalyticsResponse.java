package com.neueda.app.dtos;

import com.neueda.app.enums.AnalyticsBucketSize;
import com.neueda.app.enums.AnalyticsPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.List;

/**
 * Trading activity across all accounts for the admin home page, after the request's filters.
 * Ranges are half-open: from is included, to is not. The previous fields are null, or empty for
 * previousBuckets, for YEAR_TO_DATE, which is not compared with anything.
 *
 * Example:
 * {
 *   "period": "LAST_7_DAYS",
 *   "bucketSize": "DAY",
 *   "from": "2026-10-14T04:00:00Z",
 *   "to":   "2026-10-21T04:00:00Z",
 *   "previousFrom": "2026-10-07T04:00:00Z",
 *   "previousTo":   "2026-10-14T04:00:00Z",
 *
 *   "totals":         { "tradedValue": 184250.00, "orders": 212, "fillRate": 0.8312, "activeTraders": 14 },
 *   "previousTotals": { "tradedValue": 161900.00, "orders": 190, "fillRate": 0.8150, "activeTraders": 12 },
 *
 *   "buckets": [
 *     {
 *       "start": "2026-10-14T04:00:00Z",
 *       "future": false,
 *       "totals": { "tradedValue": 26100.00, "orders": 31, "fillRate": 0.84, "activeTraders": 9 },
 *       "byAssetClass": { "EQUITY": { ... }, "ETF": { ... }, "BOND": { ... }, "COMMODITY": { ... } },
 *       "bySide":       { "BUY": { ... }, "SELL": { ... } }
 *     }
 *     ... one per day, 7 in total
 *   ],
 *   "previousBuckets": [ ... 7 more, for 7-13 Oct ... ],
 *
 *   "topTradedInstruments": [
 *     { "symbol": "NVDA", "name": "NVIDIA Corporation", "assetClass": "EQUITY",
 *       "orders": 24, "traders": 8, "tradedValue": 31200.00, "buyValue": 19800.00 }
 *     ... up to 6
 *   ],
 *   "assetMix": [
 *     { "assetClass": "EQUITY", "tradedValue": 117900.00, "share": 0.64 }
 *     ... all 4 classes
 *   ]
 * }
 */
public record AdminAnalyticsResponse(
    @Schema(requiredMode = RequiredMode.REQUIRED) AnalyticsPeriod period,
    @Schema(requiredMode = RequiredMode.REQUIRED) AnalyticsBucketSize bucketSize,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant from,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant to,
    Instant previousFrom,
    Instant previousTo,
    @Schema(requiredMode = RequiredMode.REQUIRED) AnalyticsTotals totals,
    AnalyticsTotals previousTotals,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<AnalyticsBucket> buckets,
    // Lined up with buckets by index, so the chart can draw both periods on one axis
    @Schema(requiredMode = RequiredMode.REQUIRED) List<AnalyticsBucket> previousBuckets,
    // Highest traded value first, at most 6
    @Schema(requiredMode = RequiredMode.REQUIRED) List<TopTradedInstrument> topTradedInstruments,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<AssetMixEntry> assetMix
) {}
