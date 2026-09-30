package com.neueda.trading.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EnginePropertiesTest {

    @Test
    void testMaxDelayBelowMinDelay_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EngineProperties(Duration.ofSeconds(2), Duration.ofSeconds(1), 50, "SIM"));
    }

    @Test
    void testPriceImprovementOutOfRange_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EngineProperties(Duration.ZERO, Duration.ZERO, 10_000, "SIM"));
    }
}
