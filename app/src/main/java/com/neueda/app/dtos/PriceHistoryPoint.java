package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A stored daily bar, not an intraday quote. */
public record PriceHistoryPoint(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                                Long volume) {}
