package com.neueda.app.utils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    /** Today's date in New York, the calendar orders expire on. */
    public static LocalDate today(Instant now) {
        return now.atZone(NEW_YORK).toLocalDate();
    }

    /** The close on the given New York date, as UTC without a zone, like the other order timestamps. */
    public static LocalDateTime closeOn(LocalDate date) {
        return date.atTime(CLOSE).atZone(NEW_YORK).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** The next weekday close after now: today's if the market has not closed yet, else the next weekday's. */
    public static LocalDateTime nextClose(Instant now) {
        ZonedDateTime local = now.atZone(NEW_YORK);
        LocalDate date = local.toLocalDate();
        if (!local.toLocalTime().isBefore(CLOSE)) {
            date = date.plusDays(1);
        }
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return closeOn(date);
    }
}
