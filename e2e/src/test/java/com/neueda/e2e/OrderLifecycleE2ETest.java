package com.neueda.e2e;

import static com.neueda.e2e.support.AlpacaStub.AAPL_ASK;
import static com.neueda.e2e.support.AlpacaStub.AAPL_BID;
import static com.neueda.e2e.support.AlpacaStub.MSFT_ASK;
import static com.neueda.e2e.support.OrderRequest.limitBuy;
import static com.neueda.e2e.support.OrderRequest.marketBuy;
import static com.neueda.e2e.support.OrderRequest.marketSell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.neueda.e2e.support.AlpacaStub;
import com.neueda.e2e.support.Order;

/**
 * Orders app accepts, followed across every service: app -> order-request -> execution engine
 * (priced by the quote stream) -> order-execution -> settlement in app -> Postgres.
 */
class OrderLifecycleE2ETest extends E2ETestBase {

    @Test
    @DisplayName("MARKET BUY fills at the ask, debits cash and opens a position")
    void marketBuyFillsAtTheAsk() {
        String account = me.openAccount("10000.00");

        Order order = me.placeAndSettle(marketBuy(account, "AAPL", 10));

        assertEquals("FILLED", order.status());
        assertAmount(AAPL_ASK, order.price());
        assertAmount("8000.00", me.balance(account));
        assertEquals(10, me.sharesHeld(account, "AAPL"));
    }

    @Test
    @DisplayName("MARKET SELL fills at the bid, credits cash and reduces the position")
    void marketSellFillsAtTheBid() {
        String account = me.openAccount("10000.00");
        me.placeAndSettle(marketBuy(account, "AAPL", 10));

        Order order = me.placeAndSettle(marketSell(account, "AAPL", 4));

        assertEquals("FILLED", order.status());
        assertAmount(AAPL_BID, order.price());
        assertAmount("8799.60", me.balance(account)); // 10000 - 10 x 200.00 + 4 x 199.90
        assertEquals(6, me.sharesHeld(account, "AAPL"));
    }

    @Test
    @DisplayName("LIMIT BUY above the ask fills at once, at the ask rather than the limit")
    void marketableLimitFillsAtTheAsk() {
        String account = me.openAccount("10000.00");

        Order order = me.placeAndSettle(limitBuy(account, "MSFT", 2, "450.00"));

        assertEquals("FILLED", order.status());
        assertAmount(MSFT_ASK, order.price());
        assertAmount("9200.00", me.balance(account));
    }

    @Test
    @DisplayName("LIMIT BUY below the ask rests while quotes arrive, then cancels cleanly")
    void restingLimitCanBeCancelled() {
        String account = me.openAccount("10000.00");
        String orderId = me.place(limitBuy(account, "MSFT", 1, "100.00"));

        // a quote arrives every second; none reaches 100.00
        me.assertStaysPending(orderId, Duration.ofSeconds(5));
        Order order = me.cancel(orderId);

        assertEquals("CANCELLED", order.status());
        assertAmount("10000.00", me.balance(account));
    }

    @Test
    @DisplayName("Resting LIMIT BUY fills when a later quote crosses it, at that quote's ask")
    void restingLimitFillsWhenTheMarketMoves() {
        String account = me.openAccount("10000.00");
        String orderId = me.place(limitBuy(account, "MSFT", 1, "380.00"));
        me.assertStaysPending(orderId, Duration.ofSeconds(3)); // ask is 400.00

        try (var moved = AlpacaStub.moveQuote("MSFT", "350.00", "349.90")) {
            Order order = me.awaitSettled(orderId);

            assertEquals("FILLED", order.status());
            assertAmount("350.00", order.price());
            assertAmount("9650.00", me.balance(account));
        }
    }

    @Test
    @DisplayName("MARKET order for a symbol with no quote is rejected by the engine, with a reason")
    void unquotedMarketOrderIsRejected() {
        String account = me.openAccount("10000.00");

        // SLV has a reference price in the database, but the Alpaca stub never quotes it
        Order order = me.placeAndSettle(marketBuy(account, "SLV", 1));

        assertEquals("REJECTED", order.status());
        assertTrue(order.message().contains("No fresh quote"), order.message());
        assertAmount("10000.00", me.balance(account));
    }
}
