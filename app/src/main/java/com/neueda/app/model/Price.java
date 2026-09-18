package com.neueda.app.model;

import java.math.BigDecimal;

public record Price(
    String symbol,
    BigDecimal price
) {}
