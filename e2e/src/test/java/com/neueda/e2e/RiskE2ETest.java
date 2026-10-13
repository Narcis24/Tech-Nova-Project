package com.neueda.e2e;

import static com.neueda.e2e.support.OrderRequest.marketBuy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.neueda.e2e.support.Order;
import com.neueda.e2e.support.OrderRequest;
import com.neueda.e2e.support.Trader;

import io.restassured.path.json.JsonPath;

/**
 * Pre-trade risk warnings against the real database: limits come from the V011 tables and
 * prices from the reference prices (AAPL 195.00), so 10 AAPL in a 10,000 account is 19.5%.
 * Warnings never stop an order.
 */
class RiskE2ETest extends E2ETestBase {

    @Test
    @DisplayName("A new account gets the three default limits")
    void newAccountHasDefaultLimits() {
        String account = me.openAccount("10000.00");

        JsonPath limits = me.riskLimits(account).then().statusCode(200).extract().jsonPath();

        assertEquals(3, limits.getList("$").size());
        assertEquals(List.of("DEFAULT", "DEFAULT", "DEFAULT"), limits.getList("source"));
        assertEquals(List.of(true, true, true), limits.getList("enabled"));
    }

    @Test
    @DisplayName("Passing the holder's own limit warns, and the order still fills")
    void customLimitWarnsButOrderStillFills() {
        String account = me.openAccount("10000.00");
        me.setRiskLimit(account, "SINGLE_INSTRUMENT", "10", true).then().statusCode(200);
        OrderRequest order = marketBuy(account, "AAPL", 10);

        JsonPath warnings = me.checkRisk(order).then().statusCode(200).extract().jsonPath();

        assertEquals(1, warnings.getList("$").size());
        assertEquals("SINGLE_INSTRUMENT", warnings.getString("[0].rule"));
        assertEquals("AAPL", warnings.getString("[0].subject"));
        assertEquals("CUSTOM", warnings.getString("[0].limitSource"));
        assertAmount("19.50", new BigDecimal(warnings.getString("[0].resultingPct")));
        assertAmount("10", new BigDecimal(warnings.getString("[0].limitPct")));

        // The user accepts the warning: the same order goes through
        Order placed = me.placeAndSettle(order);

        assertEquals("FILLED", placed.status());
        assertEquals(10, me.sharesHeld(account, "AAPL"));
    }

    @Test
    @DisplayName("Resetting a limit brings back the default")
    void resetBringsBackDefault() {
        String account = me.openAccount("10000.00");
        me.setRiskLimit(account, "SINGLE_INSTRUMENT", "10", true).then().statusCode(200);

        JsonPath reset = me.resetRiskLimit(account, "SINGLE_INSTRUMENT").then().statusCode(200).extract().jsonPath();

        assertEquals("DEFAULT", reset.getString("source"));
        assertAmount("20", new BigDecimal(reset.getString("thresholdPct")));
        // 19.5% is under the default 20%
        assertTrue(me.checkRisk(marketBuy(account, "AAPL", 10)).then().statusCode(200)
            .extract().jsonPath().getList("$").isEmpty());
    }

    @Test
    @DisplayName("A switched-off rule never warns")
    void switchedOffRuleNeverWarns() {
        String account = me.openAccount("10000.00");
        me.setRiskLimit(account, "SINGLE_INSTRUMENT", "10", false).then().statusCode(200);

        assertTrue(me.checkRisk(marketBuy(account, "AAPL", 10)).then().statusCode(200)
            .extract().jsonPath().getList("$").isEmpty());
    }

    @Test
    @DisplayName("Another user can neither check nor change someone else's limits")
    void otherUsersAreRefused() {
        String account = me.openAccount("10000.00");
        Trader stranger = Trader.register();

        stranger.riskLimits(account).then().statusCode(403);
        stranger.setRiskLimit(account, "CASH_FLOOR", "5", true).then().statusCode(403);
        stranger.checkRisk(marketBuy(account, "AAPL", 10)).then().statusCode(403);
    }
}
