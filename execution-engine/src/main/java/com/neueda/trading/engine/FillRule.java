package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.neueda.trading.events.OrderPlacedEvent;

/**
 * Decides what happens to an order given a quote. Pure: no database, no clock, no socket.
 * A BUY trades at the ask and a SELL at the bid. A MARKET order fills at that price; a LIMIT
 * order fills only if that price is at or better than its limit, otherwise it waits.
 */
public final class FillRule {

    public sealed interface Decision permits Fill, Wait, Reject {
    }

    public record Fill(BigDecimal price) implements Decision {
    }

    /** A LIMIT order that cannot fill yet; it rests until a later quote crosses it. */
    public record Wait() implements Decision {
    }

    public record Reject(String reason) implements Decision {
    }

    private FillRule() {
    }

    public static Decision decide(OrderPlacedEvent order, Quote quote) {
        boolean buy = "BUY".equals(order.getSide());
        if (!buy && !"SELL".equals(order.getSide())) {
            return new Reject("Unknown side: " + order.getSide());
        }
        boolean market = "MARKET".equals(order.getOrderType());
        boolean limit = "LIMIT".equals(order.getOrderType());
        if (!market && !(limit && order.getPrice() != null)) {
            return new Reject("Invalid order: type=" + order.getOrderType() + ", limit=" + order.getPrice());
        }

        BigDecimal price = buy ? quote.ask() : quote.bid();
        if (price == null || price.signum() <= 0) {
            return market
                ? new Reject("No usable " + (buy ? "ask" : "bid") + " for " + quote.symbol())
                : new Wait();
        }
        price = price.setScale(2, RoundingMode.HALF_UP);

        if (market) {
            return new Fill(price);
        }
        int cmp = price.compareTo(order.getPrice());
        boolean crosses = buy ? cmp <= 0 : cmp >= 0;
        return crosses ? new Fill(price) : new Wait();
    }
}
