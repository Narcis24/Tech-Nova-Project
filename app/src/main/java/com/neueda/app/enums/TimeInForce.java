package com.neueda.app.enums;

/** How long a LIMIT order stays open before it expires. */
public enum TimeInForce {
    /** Until the next market close. */
    DAY,
    /** Good till cancelled, at most 90 days. */
    GTC,
    /** Good till the close on a chosen date, at most 90 days ahead. */
    GTD
}
