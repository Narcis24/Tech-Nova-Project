package com.neueda.trading.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedMarketTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static SimulatedMarket market(int maxBps, long seed) {
        EngineProperties props = new EngineProperties(Duration.ofMillis(500), Duration.ofMillis(2000), maxBps, "SIM");
        return new SimulatedMarket(props, new Random(seed), CLOCK);
    }

    private static OrderEvent order(Side side, String limit) {
        return new OrderEvent(UUID.randomUUID(), "ACC-1001", "ACME", side, 10, new BigDecimal(limit), NOW);
    }

    @Test
    void testFillPrice_Buy_NeverAboveLimit() {
        SimulatedMarket market = market(50, 1);
        BigDecimal limit = new BigDecimal("25.50");
        for (int i = 0; i < 1_000; i++) {
            BigDecimal price = market.fillPrice(Side.BUY, limit);
            assertTrue(price.compareTo(limit) <= 0, "BUY filled above limit: " + price);
            assertTrue(price.compareTo(new BigDecimal("25.37")) >= 0, "BUY improved by more than 50bps: " + price);
        }
    }

    @Test
    void testFillPrice_Sell_NeverBelowLimit() {
        SimulatedMarket market = market(50, 2);
        BigDecimal limit = new BigDecimal("25.50");
        for (int i = 0; i < 1_000; i++) {
            BigDecimal price = market.fillPrice(Side.SELL, limit);
            assertTrue(price.compareTo(limit) >= 0, "SELL filled below limit: " + price);
            assertTrue(price.compareTo(new BigDecimal("25.63")) <= 0, "SELL improved by more than 50bps: " + price);
        }
    }

    @Test
    void testFillPrice_ZeroImprovement_FillsExactlyAtLimit() {
        assertEquals(new BigDecimal("25.50"), market(0, 3).fillPrice(Side.BUY, new BigDecimal("25.50")));
    }

    @Test
    void testFillPrice_AlwaysTwoDecimalPlacesAndPositive() {
        SimulatedMarket market = market(9_999, 4);
        for (int i = 0; i < 1_000; i++) {
            BigDecimal price = market.fillPrice(Side.BUY, new BigDecimal("0.01"));
            assertEquals(2, price.scale());
            assertTrue(price.signum() > 0);
        }
    }

    @Test
    void testNextDelay_WithinConfiguredRange() {
        SimulatedMarket market = market(50, 5);
        for (int i = 0; i < 1_000; i++) {
            long ms = market.nextDelay().toMillis();
            assertTrue(ms >= 500 && ms <= 2000, "delay out of range: " + ms);
        }
    }

    @Test
    void testExecute_CopiesOrderDetailsAndStampsVenueAndTime() {
        OrderEvent order = order(Side.SELL, "12.00");

        ExecutionEvent fill = market(50, 6).execute(order);

        assertEquals(order.orderId(), fill.orderId());
        assertEquals("ACC-1001", fill.accountId());
        assertEquals(10, fill.quantity());
        assertEquals(new BigDecimal("12.00"), fill.limitPrice());
        assertEquals("SIM", fill.venue());
        assertEquals(NOW, fill.executedOn());
    }
}
