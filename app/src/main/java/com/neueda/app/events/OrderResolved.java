package com.neueda.app.events;

import com.neueda.app.enums.OrderStatus;
import java.util.UUID;

/** An order left PENDING: filled, rejected or cancelled. Published inside the transaction that did it. */
public record OrderResolved(UUID orderId, OrderStatus status) {
}
