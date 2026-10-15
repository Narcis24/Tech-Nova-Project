package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Null fields mean no actual prior session has been captured yet. */
public record BalanceHistoryResponse(LocalDate sessionDate, BigDecimal portfolioValue,
                                     BigDecimal cashBalance, OffsetDateTime capturedAt) {}
