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
        String problem = problemWith(order);
        if (problem != null) {
            return new Reject(problem);
        }

        boolean buy = "BUY".equals(order.getSide());
        boolean market = "MARKET".equals(order.getOrderType());
        BigDecimal price = usable(buy ? quote.ask() : quote.bid());
        if (price == null) {
            return market
                ? new Reject("No usable " + (buy ? "ask" : "bid") + " for " + quote.symbol())
                : new Wait();
        }
        return market || crosses(buy, price, order.getPrice()) ? new Fill(price) : new Wait();
    }

    /** Why the order can never be filled, or null if it is well formed. */
    private static String problemWith(OrderPlacedEvent order) {
        if (!"BUY".equals(order.getSide()) && !"SELL".equals(order.getSide())) {
            return "Unknown side: " + order.getSide();
        }
        boolean validLimit = "LIMIT".equals(order.getOrderType()) && order.getPrice() != null;
        if (!"MARKET".equals(order.getOrderType()) && !validLimit) {
            return "Invalid order: type=" + order.getOrderType() + ", limit=" + order.getPrice();
        }
        return null;
    }

    /** The price at 2 decimals, or null if the quote has no usable price on this side. */
    private static BigDecimal usable(BigDecimal price) {
        return price == null || price.signum() <= 0 ? null : price.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean crosses(boolean buy, BigDecimal price, BigDecimal limit) {
        int cmp = price.compareTo(limit);
        return buy ? cmp <= 0 : cmp >= 0;
    }
}
