package com.neueda.trading.engine;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * US regular trading hours: Monday to Friday, 09:30 to 16:00 New York time.
 * New York time follows daylight saving, so the hours in UTC move twice a year. Holidays are not handled.
 */
public final class MarketHours {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalTime OPEN = LocalTime.of(9, 30);
    private static final LocalTime CLOSE = LocalTime.of(16, 0);

    private MarketHours() {
    }

    public static boolean isOpen(Instant now) {
        ZonedDateTime local = now.atZone(NEW_YORK);
        DayOfWeek day = local.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = local.toLocalTime();
        return !time.isBefore(OPEN) && time.isBefore(CLOSE);
    }
}
