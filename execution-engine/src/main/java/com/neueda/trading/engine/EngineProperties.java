package com.neueda.trading.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tuning for the simulated market, bound from {@code engine.*}.
 *
 * @param minDelay               shortest time the market takes to fill an order
 * @param maxDelay               longest time the market takes to fill an order
 * @param maxPriceImprovementBps largest price improvement over the limit, in basis points (1 bp = 0.01%)
 * @param venue                  name stamped on every fill
 */
@ConfigurationProperties("engine")
public record EngineProperties(
        Duration minDelay,
        Duration maxDelay,
        int maxPriceImprovementBps,
        String venue
) {
    public EngineProperties {
        if (minDelay.isNegative() || maxDelay.compareTo(minDelay) < 0) {
            throw new IllegalArgumentException("engine delays must satisfy 0 <= min-delay <= max-delay");
        }
        if (maxPriceImprovementBps < 0 || maxPriceImprovementBps >= 10_000) {
            throw new IllegalArgumentException("engine.max-price-improvement-bps must be between 0 and 9999");
        }
    }
}
