package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.neueda.trading.engine.FillRule.Decision;
import com.neueda.trading.engine.FillRule.Fill;
import com.neueda.trading.engine.FillRule.Reject;
import com.neueda.trading.engine.FillRule.Wait;
import com.neueda.events.OrderPlacedEvent;

class FillRuleTest {

    // bid 99.50, ask 100.50
    private static final Quote QUOTE =
        new Quote("AAPL", new BigDecimal("99.50"), new BigDecimal("100.50"), Instant.now());

    private static OrderPlacedEvent order(String side, String type, String limit) {
        return new OrderPlacedEvent(UUID.randomUUID(), "ACC1", "AAPL", side, type, 10,
            limit == null ? null : new BigDecimal(limit), "key");
    }

    private static void assertFill(String price, Decision d) {
        assertEquals(new BigDecimal(price), assertInstanceOf(Fill.class, d).price());
    }

    @Test
    void marketBuyFillsAtAsk() {
        assertFill("100.50", FillRule.decide(order("BUY", "MARKET", null), QUOTE));
    }

    @Test
    void marketSellFillsAtBid() {
        assertFill("99.50", FillRule.decide(order("SELL", "MARKET", null), QUOTE));
    }

    @Test
    void limitBuyFillsAtAskWhenLimitIsAtOrAboveIt() {
        assertFill("100.50", FillRule.decide(order("BUY", "LIMIT", "100.50"), QUOTE));
        assertFill("100.50", FillRule.decide(order("BUY", "LIMIT", "120.00"), QUOTE));
    }

    @Test
    void limitBuyBelowAskWaits() {
        assertInstanceOf(Wait.class, FillRule.decide(order("BUY", "LIMIT", "100.49"), QUOTE));
    }

    @Test
    void limitSellFillsAtBidWhenLimitIsAtOrBelowIt() {
        assertFill("99.50", FillRule.decide(order("SELL", "LIMIT", "99.50"), QUOTE));
        assertFill("99.50", FillRule.decide(order("SELL", "LIMIT", "90.00"), QUOTE));
    }

    @Test
    void limitSellAboveBidWaits() {
        assertInstanceOf(Wait.class, FillRule.decide(order("SELL", "LIMIT", "99.51"), QUOTE));
    }

    @Test
    void marketOrderWithoutUsableQuoteSideIsRejectedButLimitWaits() {
        Quote noAsk = new Quote("AAPL", new BigDecimal("99.50"), BigDecimal.ZERO, Instant.now());
        assertInstanceOf(Reject.class, FillRule.decide(order("BUY", "MARKET", null), noAsk));
        assertInstanceOf(Wait.class, FillRule.decide(order("BUY", "LIMIT", "100.00"), noAsk));
        Quote noBid = new Quote("AAPL", null, new BigDecimal("100.50"), Instant.now());
        assertInstanceOf(Reject.class, FillRule.decide(order("SELL", "MARKET", null), noBid));
    }

    @Test
    void malformedOrdersAreRejected() {
        assertInstanceOf(Reject.class, FillRule.decide(order("HOLD", "MARKET", null), QUOTE));
        assertInstanceOf(Reject.class, FillRule.decide(order("BUY", "LIMIT", null), QUOTE));
        assertInstanceOf(Reject.class, FillRule.decide(order("BUY", "STOP", "100"), QUOTE));
    }
}
