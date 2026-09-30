package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * A stand-in for a real exchange. Every order is treated as a limit order
 * and filled in full, after a random delay, at a price at or better than
 * its limit: a BUY fills at or below the limit, a SELL at or above it, by up
 * to {@link EngineProperties#maxPriceImprovementBps()}. Randomness and time
 * are injected so tests can pin both.
 */
public class SimulatedMarket {

    private static final BigDecimal BPS = BigDecimal.valueOf(10_000);
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");

    private final EngineProperties properties;
    private final RandomGenerator random;
    private final Clock clock;

    public SimulatedMarket(EngineProperties properties, RandomGenerator random, Clock clock) {
        this.properties = properties;
        this.random = random;
        this.clock = clock;
    }

    public Duration nextDelay() {
        long min = properties.minDelay().toMillis();
        long max = properties.maxDelay().toMillis();
        return Duration.ofMillis(min == max ? min : random.nextLong(min, max + 1));
    }

    public ExecutionEvent execute(OrderEvent order) {
        return new ExecutionEvent(UUID.randomUUID(), order.orderId(), order.accountId(), order.symbol(),
                order.side(), order.quantity(), fillPrice(order.side(), order.price()), order.price(),
                properties.venue(), clock.instant());
    }

    BigDecimal fillPrice(Side side, BigDecimal limit) {
        int bps = random.nextInt(properties.maxPriceImprovementBps() + 1);
        BigDecimal improvement = limit.multiply(BigDecimal.valueOf(bps)).divide(BPS, 2, RoundingMode.DOWN);
        BigDecimal price = side == Side.BUY ? limit.subtract(improvement) : limit.add(improvement);
        return price.max(MIN_PRICE).setScale(2, RoundingMode.HALF_EVEN);
    }
}
