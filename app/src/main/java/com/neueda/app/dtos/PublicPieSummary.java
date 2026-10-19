package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One row of the leaderboard. returnPct is in percent over the requested window; demo marks the seeded pies. */
public record PublicPieSummary(UUID pieId, String name, String description, String authorUsername, int sliceCount,
                               BigDecimal returnPct, long followers, LocalDateTime publishedAt, boolean demo) {}
