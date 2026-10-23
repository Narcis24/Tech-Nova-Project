package com.neueda.app.utils;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketHoursTest {

    @Test
    void closeIsAt16NewYorkTimeInSummerAndWinter() {
        assertEquals(LocalDateTime.of(2026, 10, 21, 20, 0), MarketHours.closeOn(LocalDate.of(2026, 10, 21)));
        assertEquals(LocalDateTime.of(2026, 12, 1, 21, 0), MarketHours.closeOn(LocalDate.of(2026, 12, 1)));
    }

    @Test
    void nextCloseIsTodaysUntilTheMarketCloses() {
        // Wednesday 2026-10-21: 09:00 and 15:59 in New York
        assertEquals(LocalDateTime.of(2026, 10, 21, 20, 0), MarketHours.nextClose(Instant.parse("2026-10-21T13:00:00Z")));
        assertEquals(LocalDateTime.of(2026, 10, 21, 20, 0), MarketHours.nextClose(Instant.parse("2026-10-21T19:59:00Z")));
    }

    @Test
    void nextCloseAfterTheCloseIsTheNextWeekdays() {
        // Wednesday 16:00 → Thursday
        assertEquals(LocalDateTime.of(2026, 10, 22, 20, 0), MarketHours.nextClose(Instant.parse("2026-10-21T20:00:00Z")));
        // Friday evening and Saturday → Monday
        assertEquals(LocalDateTime.of(2026, 10, 26, 20, 0), MarketHours.nextClose(Instant.parse("2026-10-23T22:00:00Z")));
        assertEquals(LocalDateTime.of(2026, 10, 26, 20, 0), MarketHours.nextClose(Instant.parse("2026-10-24T15:00:00Z")));
    }

    @Test
    void todayIsTheNewYorkDate() {
        // 02:00 UTC on the 22nd is still the 21st in New York
        assertEquals(LocalDate.of(2026, 10, 21), MarketHours.today(Instant.parse("2026-10-22T02:00:00Z")));
    }
}
