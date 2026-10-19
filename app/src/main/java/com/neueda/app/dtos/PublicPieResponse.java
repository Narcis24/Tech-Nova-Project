package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A public pie. returnPct is the ALL window; returns holds each window (7D, 30D, 90D, ALL) in percent,
 * null where the pie is too young. Nothing here depends on any account's holdings or money.
 */
public record PublicPieResponse(UUID pieId, String name, String description, String authorUsername, int sliceCount,
                                BigDecimal returnPct, long followers, LocalDateTime publishedAt, boolean demo,
                                List<PieSliceResponse> slices, BigDecimal cashPct, List<IndexPoint> points,
                                Map<String, BigDecimal> returns) {}
