package com.neueda.events;

import java.util.UUID;

/** The holder cancelled a PENDING order; the execution engine stops waiting on it. */
public record OrderCancelledEvent(
    UUID orderId
) {
}
