package com.neueda.events;

public final class EventTypes {
    public static final String ORDER_PLACED = "ORDER_PLACED";
    public static final String ORDER_EXECUTED = "ORDER_EXECUTED";
    public static final String ORDER_REJECTED = "ORDER_REJECTED";
    // The execution engine has (re)started and holds no waiting orders; the app sends them again
    public static final String ENGINE_STARTED = "ENGINE_STARTED";
    private EventTypes() {}
}
