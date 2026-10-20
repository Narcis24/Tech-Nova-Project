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
