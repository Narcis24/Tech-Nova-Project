package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.time.Instant;

/** Latest top-of-book quote for a symbol. asOf is when the poller received it. */
public record Quote(String symbol, BigDecimal bid, BigDecimal ask, Instant asOf) {
}
