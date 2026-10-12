package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class MarketDataPropertiesTest {

    @Test
    void createsValidPropertiesWithDefaults() {
        MarketDataProperties props = new MarketDataProperties(
            Arrays.asList("AAPL", "MSFT"),
            120,
            25,
            2000,
            600,
            "https://data.alpaca.markets",
            "test-key",
            "test-secret"
        );

        assertEquals(Arrays.asList("AAPL", "MSFT"), props.symbols());
        assertEquals(120, props.intervalSeconds());
        assertEquals(25, props.batchSize());
        assertEquals(2000, props.dailyQuota());
        assertEquals(600, props.maxQuoteAgeSeconds());
        assertEquals("https://data.alpaca.markets", props.baseUrl());
        assertEquals("test-key", props.keyId());
        assertEquals("test-secret", props.secretKey());
    }

    @Test
    void throwsWhenSymbolsIsEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
            new MarketDataProperties(
                Collections.emptyList(),
                120, 25, 2000, 600, "http://x", "", ""
            )
        );
    }

    @Test
    void throwsWhenSymbolsIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
            new MarketDataProperties(
                null,
                120, 25, 2000, 600, "http://x", "", ""
            )
        );
    }

    @Test
    void throwsWhenIntervalSecondsIsZero() {
        assertThrows(IllegalArgumentException.class, () ->
            new MarketDataProperties(
                Arrays.asList("AAPL"),
                0, 25, 2000, 600, "http://x", "", ""
            )
        );
    }

    @Test
    void throwsWhenIntervalSecondsIsNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            new MarketDataProperties(
                Arrays.asList("AAPL"),
                -1, 25, 2000, 600, "http://x", "", ""
            )
        );
    }

    @Test
    void throwsWhenBatchSizeIsZero() {
        assertThrows(IllegalArgumentException.class, () ->
            new MarketDataProperties(
                Arrays.asList("AAPL"),
                120, 0, 2000, 600, "http://x", "", ""
            )
        );
    }

    @Test
    void throwsWhenBatchSizeIsNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            new MarketDataProperties(
                Arrays.asList("AAPL"),
                120, -1, 2000, 600, "http://x", "", ""
            )
        );
    }

    @Test
    void throwsWhenRequestsPerDayExceedsQuota() {
        // 26 symbols, 25 batch size, 120 second interval
        // = ceil(26/25) * ceil(86400/120) = 2 * 720 = 1440 requests/day, quota = 1439
        assertThrows(IllegalArgumentException.class, () ->
            new MarketDataProperties(
                Arrays.asList("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10",
                    "B1", "B2", "B3", "B4", "B5", "B6", "B7", "B8", "B9", "B10",
                    "C1", "C2", "C3", "C4", "C5", "C6", "C7"),
                120, 25, 1439, 600, "http://x", "", ""
            )
        );
    }

    @Test
    void acceptsAtBoundaryQuota() {
        // 10 symbols, 10 batch size, 120 second interval
        // = ceil(10/10) * ceil(86400/120) = 1 * 720 = 720 requests/day
        MarketDataProperties props = new MarketDataProperties(
            Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"),
            120, 10, 720, 600, "http://x", "", ""
        );

        assertEquals(10, props.symbols().size());
    }

    @Test
    void rejectsSlightlyOverBoundaryQuota() {
        // 10 symbols, 10 batch size, 120 second interval
        // = ceil(10/10) * ceil(86400/120) = 1 * 720 = 720 requests/day, quota = 719
        assertThrows(IllegalArgumentException.class, () ->
            new MarketDataProperties(
                Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"),
                120, 10, 719, 600, "http://x", "", ""
            )
        );
    }

    @Test
    void acceptsEmptyCredentials() {
        MarketDataProperties props = new MarketDataProperties(
            Arrays.asList("AAPL"),
            120, 25, 2000, 600, "http://x", "", ""
        );

        assertEquals("", props.keyId());
        assertEquals("", props.secretKey());
    }

    @Test
    void acceptsCustomBaseUrl() {
        String customUrl = "https://custom.example.com";
        MarketDataProperties props = new MarketDataProperties(
            Arrays.asList("AAPL"),
            120, 25, 2000, 600, customUrl, "key", "secret"
        );

        assertEquals(customUrl, props.baseUrl());
    }
}
