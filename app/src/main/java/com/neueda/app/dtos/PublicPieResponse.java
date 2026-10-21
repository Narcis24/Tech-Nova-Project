package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A public allocation template, with no account holdings or money. */
public record PublicPieResponse(UUID pieId, String name, String description, String authorUsername, int sliceCount,
                                long followers, LocalDateTime publishedAt, boolean demo,
                                List<PieSliceResponse> slices, BigDecimal cashPct) {}
