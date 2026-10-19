package com.neueda.app.enums;

import java.util.Arrays;

/** A leaderboard window. ALL runs from the publish day and has no minimum age. */
public enum PieWindow {
    WEEK("7D", 7),
    MONTH("30D", 30),
    QUARTER("90D", 90),
    ALL("ALL", null);

    private final String label;
    private final Integer days;

    PieWindow(String label, Integer days) {
        this.label = label;
        this.days = days;
    }

    public String label() {
        return label;
    }

    /** Length in days, or null for ALL. */
    public Integer days() {
        return days;
    }

    public static PieWindow parse(String value) {
        return Arrays.stream(values())
            .filter(w -> w.label.equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("window must be one of 7D, 30D, 90D, ALL"));
    }
}
