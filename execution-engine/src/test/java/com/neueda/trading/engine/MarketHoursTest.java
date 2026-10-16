package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class MarketHoursTest {

    private static boolean open(String utc) {
        return MarketHours.isOpen(Instant.parse(utc));
    }

    @Test
    void openOnAWeekdayBetween0930And1600NewYorkTime() {
        // October: New York is UTC-4
        assertTrue(open("2026-10-14T13:30:00Z"));   // Wed 09:30, the open
        assertTrue(open("2026-10-14T19:59:59Z"));   // Wed 15:59:59
    }

    @Test
    void closedBeforeTheOpenAndFromTheClose() {
        assertFalse(open("2026-10-14T13:29:59Z"));  // Wed 09:29:59
        assertFalse(open("2026-10-14T20:00:00Z"));  // Wed 16:00, the close
        assertFalse(open("2026-10-16T07:24:00Z"));  // Fri 03:24
    }

    @Test
    void closedAtTheWeekend() {
        assertFalse(open("2026-10-17T15:00:00Z"));  // Sat 11:00
        assertFalse(open("2026-10-18T15:00:00Z"));  // Sun 11:00
    }

    @Test
    void followsNewYorkDaylightSaving() {
        // December: New York is UTC-5, so 09:30 is 14:30 UTC
        assertFalse(open("2026-12-02T14:00:00Z"));  // Wed 09:00
        assertTrue(open("2026-12-02T14:30:00Z"));   // Wed 09:30
    }
}
