package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A stored daily close, not an intraday quote. */
public record PriceHistoryPoint(LocalDate date, BigDecimal close) {}
