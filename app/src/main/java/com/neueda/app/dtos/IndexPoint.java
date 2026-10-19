package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A pie's strategy index on one day. */
public record IndexPoint(LocalDate date, BigDecimal value) {}
