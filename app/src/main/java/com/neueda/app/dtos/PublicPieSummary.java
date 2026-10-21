package com.neueda.app.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

/** A published allocation template; demo marks the seeded pies. */
public record PublicPieSummary(UUID pieId, String name, String description, String authorUsername, int sliceCount,
                               long followers, LocalDateTime publishedAt, boolean demo) {}
