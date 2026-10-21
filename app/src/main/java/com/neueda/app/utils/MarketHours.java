package com.neueda.app.utils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * US regular trading hours: Monday to Friday, 09:30 to 16:00 New York time, the hours the
 * execution engine fills orders in. Holidays are not handled, as there.
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
