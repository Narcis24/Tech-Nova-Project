package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.time.Instant;

/** The latest bid and ask the execution engine received from the market data feed, and when. */
public record QuoteResponse(String symbol, BigDecimal bid, BigDecimal ask, Instant asOf) {}
