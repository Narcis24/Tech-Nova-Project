package com.neueda.app.repositories;

import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One order as the admin analytics need it, read in a single query with its instrument and owner
 * instead of loading Order entities and their lazy associations one by one.
 */
public record OrderActivity(
    String symbol,
    String instrumentName,
    AssetClass assetClass,
    OrderSide side,
    OrderType orderType,
    OrderStatus status,
    int quantity,
    BigDecimal price,          // the fill price once FILLED, the limit price before that
    LocalDateTime createdOn,   // UTC, as stored
    String trader              // the account's owner; null for unclaimed seed accounts
) {
    /** Quantity × price; only meaningful as traded value when the order is FILLED. */
    public BigDecimal value() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
