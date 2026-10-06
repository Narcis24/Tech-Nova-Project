package com.neueda.e2e;

import static com.neueda.e2e.support.OrderRequest.limitBuy;
import static com.neueda.e2e.support.OrderRequest.marketBuy;
import static com.neueda.e2e.support.OrderRequest.marketSell;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.neueda.e2e.support.OrderRequest;
import com.neueda.e2e.support.SeedAccounts;

/** Orders app refuses up front, before anything reaches Kafka. */
class OrderRefusalE2ETest extends E2ETestBase {

    @Test
    @DisplayName("BUY the account cannot afford is refused with 400")
    void unaffordableBuyIsRefused() {
        String account = me.openAccount("100.00");

        me.submit(marketBuy(account, "AAPL", 1)).then().statusCode(400);
    }

    @Test
    @DisplayName("SELL of shares the account does not hold is refused with 400")
    void sellWithoutHoldingsIsRefused() {
        String account = me.openAccount("100.00");

        me.submit(marketSell(account, "AAPL", 1)).then().statusCode(400);
    }

    @Test
    @DisplayName("Order on an inactive account is refused with 400")
    void inactiveAccountIsRefused() throws SQLException {
        // the API cannot deactivate an account, so claim the seeded inactive one
        SeedAccounts.claim(SeedAccounts.INACTIVE, me);

        me.submit(marketBuy(SeedAccounts.INACTIVE, "AAPL", 1)).then().statusCode(400);
    }

    @Test
    @DisplayName("Reusing an idempotency key is refused with 409")
    void duplicateIdempotencyKeyIsRefused() {
        String account = me.openAccount("100.00");
        OrderRequest order = limitBuy(account, "MSFT", 1, "1.00");
        me.place(order);

        me.submit(order).then().statusCode(409);
    }
}
