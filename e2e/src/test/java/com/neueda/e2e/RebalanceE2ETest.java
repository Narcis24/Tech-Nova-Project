package com.neueda.e2e;

import static com.neueda.e2e.support.AlpacaStub.AAPL_ASK;
import static com.neueda.e2e.support.OrderRequest.limitBuy;
import static com.neueda.e2e.support.OrderRequest.marketBuy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.neueda.e2e.support.Trader;

import io.restassured.path.json.JsonPath;

/**
 * Rebalance runs against the real stack: a run sells first, buys with what the sales raised,
 * and ends COMPLETED or PARTIAL. Every test opens its own account and pie, and waits for its
 * run to end rather than racing it.
 */
class RebalanceE2ETest extends E2ETestBase {

    private static final Set<String> RUN_ENDED = Set.of("COMPLETED", "PARTIAL", "CANCELLED");
    private static final Set<String> LEG_ENDED = Set.of("FILLED", "REJECTED", "CANCELLED");

    @Test
    void allCashAccountBuysToWithinOneShareOfTarget() {
        String account = me.openAccount("10000.00");
        link(account, createPie("Growth", Map.of("AAPL", 50, "MSFT", 30)));
        int ordersBefore = orderCount(account);

        JsonPath run = rebalance(account);

        assertEquals("COMPLETED", run.getString("status"));
        assertEquals(1, run.getInt("targetVersion"));
        assertEquals(account, run.getString("accountId"));
        assertEquals(List.of("BUY", "BUY"), run.getList("legs.phase"));
        assertEquals(List.of("BUY", "BUY"), run.getList("legs.side"));
        assertTrue(run.getList("legs.status").stream().allMatch("FILLED"::equals), run.prettify());
        assertTrue(run.getList("legs.failureReason").stream().allMatch(r -> r == null), run.prettify());
        assertLegsMatchOrders(run);
        assertWithinOneShare(account, "AAPL", 50);
        assertWithinOneShare(account, "MSFT", 30);
        assertCashNotNegative(account);
        assertEquals(1, accountPie(account).getInt("appliedVersion"));

        // reading the run again must not place anything more
        String runId = run.getString("runId");
        run(runId);
        run(runId);
        assertEquals(ordersBefore + 2, orderCount(account));
        assertEquals(run.getList("legs.orderId"), run(runId).getList("legs.orderId"));
    }

    @Test
    void holdingsOutsideThePieAreSoldAndTheBuysAreFundedByThem() {
        String account = me.openAccount("10000.00");
        // 49 AAPL leaves 200.00 of cash, far too little for the MSFT slice without the sale
        assertEquals("FILLED", me.placeAndSettle(marketBuy(account, "AAPL", 49)).status());
        assertAmount("200.00", me.balance(account));
        link(account, createPie("Microsoft only", Map.of("MSFT", 80)));
        int ordersBefore = orderCount(account);

        JsonPath run = rebalance(account);

        assertEquals("COMPLETED", run.getString("status"), run.prettify());
        assertEquals(List.of("AAPL"), run.getList("legs.findAll { it.phase == 'SELL' }.symbol"));
        assertEquals(List.of("SELL"), run.getList("legs.findAll { it.phase == 'SELL' }.side"));
        assertEquals(List.of(49), run.getList("legs.findAll { it.phase == 'SELL' }.quantity"));
        assertEquals(List.of("MSFT"), run.getList("legs.findAll { it.phase == 'BUY' }.symbol"));
        assertTrue(run.getList("legs.status").stream().allMatch("FILLED"::equals), run.prettify());
        assertLegsMatchOrders(run);
        assertEquals(0, me.sharesHeld(account, "AAPL"));
        assertWithinOneShare(account, "MSFT", 80);
        assertCashNotNegative(account);
        assertEquals(ordersBefore + 2, orderCount(account));
    }

    @Test
    void aRunKeepsItsPieVersionWhileThePieMovesOn() {
        String account = me.openAccount("10000.00");
        String pie = createPie("Shifting", Map.of("AAPL", 50, "MSFT", 30));
        link(account, pie);

        JsonPath first = rebalance(account);
        me.request().body(body("Shifting", Map.of("AAPL", 30, "MSFT", 50))).put("/v1/pies/" + pie)
            .then().statusCode(200).body("version", equalTo(2));

        assertEquals(1, first.getInt("targetVersion"));
        assertEquals(1, run(first.getString("runId")).getInt("targetVersion"));
        JsonPath linked = accountPie(account);
        assertEquals(1, linked.getInt("appliedVersion"));
        assertEquals(2, linked.getInt("latestVersion"));

        JsonPath second = rebalance(account);

        assertEquals("COMPLETED", second.getString("status"), second.prettify());
        assertEquals(2, second.getInt("targetVersion"));
        assertEquals(1, run(first.getString("runId")).getInt("targetVersion"));
        assertEquals(2, accountPie(account).getInt("appliedVersion"));
        assertLegsMatchOrders(second);
        assertWithinOneShare(account, "AAPL", 30);
        assertWithinOneShare(account, "MSFT", 50);
        assertCashNotNegative(account);
    }

    @Test
    void rejectedBuyEndsPartialWithItsReasonAndLeavesTheCashAlone() {
        String account = me.openAccount("10000.00");
        // the Alpaca stub never quotes SLV, so the engine rejects its MARKET buy
        String pie = createPie("With silver", Map.of("AAPL", 50, "SLV", 30));
        link(account, pie);

        JsonPath run = rebalance(account);

        assertEquals("PARTIAL", run.getString("status"), run.prettify());
        assertEquals("FILLED", run.getString("legs.find { it.symbol == 'AAPL' }.status"));
        assertEquals("REJECTED", run.getString("legs.find { it.symbol == 'SLV' }.status"));
        assertTrue(run.getString("legs.find { it.symbol == 'SLV' }.failureReason").contains("No fresh quote"),
            run.prettify());
        assertLegsMatchOrders(run);
        assertEquals(0, me.sharesHeld(account, "SLV"));
        // only the AAPL fill moved cash
        int apple = me.sharesHeld(account, "AAPL");
        assertTrue(apple > 0);
        assertAmount(new BigDecimal("10000.00").subtract(AAPL_ASK.multiply(BigDecimal.valueOf(apple))),
            me.balance(account));
        assertCashNotNegative(account);
        // a partly done run still counts as applied
        assertEquals(1, accountPie(account).getInt("appliedVersion"));
    }

    @Test
    void pendingOrderOnTheAccountBlocksARunUntilItIsCancelled() {
        String account = me.openAccount("10000.00");
        link(account, createPie("Blocked", Map.of("AAPL", 10)));
        // a LIMIT far below the market stays PENDING
        String pending = me.place(limitBuy(account, "MSFT", 1, "1.00"));

        me.request().post("/v1/accounts/" + account + "/rebalance").then().statusCode(409);

        assertEquals(1, orderCount(account));
        assertAmount("10000.00", me.balance(account));
        me.cancel(pending);
        assertEquals("COMPLETED", rebalance(account).getString("status"));
    }

    @Test
    void otherUsersCannotStartReadOrCancelARun() {
        String account = me.openAccount("10000.00");
        link(account, createPie("Private", Map.of("AAPL", 10)));
        JsonPath run = rebalance(account);
        String runId = run.getString("runId");
        int ordersBefore = orderCount(account);
        Trader other = Trader.register();

        other.request().post("/v1/accounts/" + account + "/rebalance").then().statusCode(403);
        other.request().get("/v1/rebalance-runs/" + runId).then().statusCode(403);
        other.request().post("/v1/rebalance-runs/" + runId + "/cancel").then().statusCode(403);

        assertEquals(run.getString("status"), run(runId).getString("status"));
        assertEquals(ordersBefore, orderCount(account));
        assertEquals(List.of(), other.accountIds());
    }

    // ---- helpers ----

    /** Starts a run, which the API accepts with 202, and waits for it to end. */
    private JsonPath rebalance(String account) {
        String runId = me.request().post("/v1/accounts/" + account + "/rebalance")
            .then().statusCode(202).extract().path("runId");
        return await("run " + runId + " to end").atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(500))
            .until(() -> run(runId), r -> RUN_ENDED.contains(r.getString("status")));
    }

    private JsonPath run(String runId) {
        return me.request().get("/v1/rebalance-runs/" + runId).then().statusCode(200).extract().jsonPath();
    }

    private JsonPath accountPie(String account) {
        return me.request().get("/v1/accounts/" + account + "/pie").then().statusCode(200).extract().jsonPath();
    }

    private int orderCount(String account) {
        return me.request().get("/v1/accounts/" + account + "/orders").then().statusCode(200)
            .extract().jsonPath().getList("$").size();
    }

    private String createPie(String name, Map<String, Integer> weights) {
        return me.request().body(body(name, weights)).post("/v1/pies").then().statusCode(201).extract().path("pieId");
    }

    private void link(String account, String pie) {
        me.request().body(Map.of("pieId", pie)).put("/v1/accounts/" + account + "/pie").then().statusCode(200);
    }

    private static Map<String, Object> body(String name, Map<String, Integer> weights) {
        return Map.of("name", name, "slices", weights.entrySet().stream()
            .map(e -> Map.of("symbol", e.getKey(), "weightPct", e.getValue())).toList());
    }

    /** Every leg is resolved, and says the same as the order it placed. */
    private void assertLegsMatchOrders(JsonPath run) {
        List<Map<String, Object>> legs = run.getList("legs");
        assertFalse(legs.isEmpty(), run.prettify());
        for (Map<String, Object> leg : legs) {
            String status = (String) leg.get("status");
            assertTrue(LEG_ENDED.contains(status), "leg not resolved: " + leg);
            String orderId = (String) leg.get("orderId");
            assertNotNull(orderId, "leg has no order: " + leg);
            assertEquals(status, me.order(orderId).status(), "leg and order disagree: " + leg);
        }
    }

    /** Uses the same portfolio reference prices as the planner, rather than execution bid/ask quotes. */
    private void assertWithinOneShare(String account, String symbol, int weightPct) {
        JsonPath snapshot = me.request().get("/v1/accounts/" + account + "/snapshot")
            .then().statusCode(200).extract().jsonPath();
        BigDecimal total = new BigDecimal(snapshot.getString("totalPortfolioValue"));
        BigDecimal price = new BigDecimal(snapshot.getString("positions.find { it.symbol == '" + symbol + "' }.currentPrice"));
        double target = total.doubleValue() * weightPct / 100 / price.doubleValue();
        int held = snapshot.getInt("positions.find { it.symbol == '" + symbol + "' }.quantity");
        assertTrue(Math.abs(held - target) <= 1, symbol + ": held " + held + " but target is " + target);
    }

    private void assertCashNotNegative(String account) {
        assertTrue(me.balance(account).signum() >= 0, "cash went negative: " + me.balance(account));
    }
}
