package com.neueda.trading.engine;

/** The "type" of each decision journal line, and the "decision" of a DECISION line. */
public final class JournalTypes {

    public static final String ENGINE_STARTED = "ENGINE_STARTED";
    public static final String ORDER_RECEIVED = "ORDER_RECEIVED";
    public static final String QUOTE_RECEIVED = "QUOTE_RECEIVED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String DECISION = "DECISION";

    public static final String FILL = "FILL";
    public static final String REJECT = "REJECT";
    // not WAIT, which clashes with Object.wait(); the journal value stays "WAIT" so recorded days still replay
    public static final String WAITING = "WAIT";
    public static final String QUEUED_MARKET_CLOSED = "QUEUED_MARKET_CLOSED";

    private JournalTypes() {
    }
}
