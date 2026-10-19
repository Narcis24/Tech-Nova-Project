package com.neueda.e2e;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.neueda.e2e.support.Trader;

import io.restassured.path.json.JsonPath;

/**
 * Copy trading against the real stack: the author is {@code me}, and each test registers a copier
 * with their own account. Runs are waited on rather than raced.
 */
class CopyTradingE2ETest extends E2ETestBase {

    private static final Set<String> RUN_ENDED = Set.of("COMPLETED", "PARTIAL", "CANCELLED");

    @Test
    void copierFollowsTheAuthorsNewVersion() {
        String pie = publishedPie("Followed", Map.of("AAPL", 50, "MSFT", 30));
        Trader copier = Trader.register();
        String account = copier.openAccount("10000.00");

        copier.request().body(Map.of("pieId", pie)).post(pieOf(account) + "/copy-preview").then().statusCode(200);
        copier.request().get(pieOf(account)).then().statusCode(404); // a preview links nothing
        JsonPath first = awaitRun(copier, copy(copier, account, pie));

        assertEquals("COMPLETED", first.getString("status"), first.prettify());
        JsonPath linked = accountPie(copier, account);
        assertEquals("COPY", linked.getString("mode"));
        assertEquals(1, linked.getInt("appliedVersion"));
        assertTrue(linked.getBoolean("autoSync"));
        assertWithinOneShare(copier, account, "AAPL", 50);
        me.request().get("/v1/pies/public/" + pie).then().statusCode(200).body("followers", equalTo(1));

        me.request().body(body("Followed", Map.of("AAPL", 30, "MSFT", 50))).put("/v1/pies/" + pie)
            .then().statusCode(200).body("version", equalTo(2));

        JsonPath synced = await("copier to reach version 2").atMost(Duration.ofSeconds(90))
            .pollInterval(Duration.ofMillis(500))
            .until(() -> accountPie(copier, account),
                p -> p.getInt("appliedVersion") == 2 && p.getString("activeRunId") == null);
        assertFalse(synced.getBoolean("updateAvailable"));
        assertFalse(synced.getBoolean("syncPending"));
        assertWithinOneShare(copier, account, "AAPL", 30);
        assertWithinOneShare(copier, account, "MSFT", 50);
        assertTrue(copier.balance(account).signum() >= 0);

        // the copier follows the pie but can't read or change the author's copy of it
        copier.request().get("/v1/pies/" + pie).then().statusCode(404);
        copier.request().body(body("Hijacked", Map.of("AAPL", 10))).put("/v1/pies/" + pie)
            .then().statusCode(404);
    }

    @Test
    void withAutoSyncOffTheCopierIsOfferedTheUpdateAndKeepsHoldingsAfterUnpublish() {
        String pie = publishedPie("Manual", Map.of("AAPL", 40));
        String ownAccount = me.openAccount("1000.00");
        me.request().body(Map.of("pieId", pie)).post(pieOf(ownAccount) + "/copy").then().statusCode(409);

        Trader copier = Trader.register();
        String account = copier.openAccount("10000.00");
        assertEquals("COMPLETED", awaitRun(copier, copy(copier, account, pie)).getString("status"));
        copier.request().body(Map.of("pieId", pie)).post(pieOf(account) + "/copy").then().statusCode(409);
        copier.request().body(Map.of("autoSync", false)).patch(pieOf(account)).then().statusCode(200)
            .body("autoSync", equalTo(false));
        int apple = copier.sharesHeld(account, "AAPL");
        int orders = orderCount(copier, account);

        me.request().body(body("Manual", Map.of("AAPL", 60))).put("/v1/pies/" + pie).then().statusCode(200);

        JsonPath behind = accountPie(copier, account);
        assertEquals(1, behind.getInt("appliedVersion"));
        assertEquals(2, behind.getInt("latestVersion"));
        assertTrue(behind.getBoolean("updateAvailable"));
        assertNull(behind.getString("activeRunId"));
        assertEquals(orders, orderCount(copier, account));

        me.request().post("/v1/pies/" + pie + "/unpublish").then().statusCode(200);

        JsonPath unpublished = accountPie(copier, account);
        assertFalse(unpublished.getBoolean("published"));
        assertFalse(unpublished.getBoolean("updateAvailable"));
        assertEquals(1, unpublished.getInt("appliedVersion"));
        assertEquals(apple, copier.sharesHeld(account, "AAPL"));
        assertEquals(orders, orderCount(copier, account));
    }

    @Test
    void makeItMineRelinksToAPrivateDuplicateWithoutTrading() {
        String pie = publishedPie("Borrowed", Map.of("MSFT", 50));
        Trader copier = Trader.register();
        String account = copier.openAccount("10000.00");
        assertEquals("COMPLETED", awaitRun(copier, copy(copier, account, pie)).getString("status"));
        int orders = orderCount(copier, account);

        JsonPath mine = copier.request().post(pieOf(account) + "/make-mine").then().statusCode(200)
            .extract().jsonPath();

        assertEquals("OWN", mine.getString("mode"));
        assertNotEquals(pie, mine.getString("pieId"));
        assertEquals("Borrowed", mine.getString("pieName"));
        copier.request().get("/v1/pies/" + mine.getString("pieId")).then().statusCode(200)
            .body("visibility", equalTo("PRIVATE"));
        assertEquals(orders, orderCount(copier, account));
        me.request().get("/v1/pies/public/" + pie).then().statusCode(200).body("followers", equalTo(0));
    }

    // ---- helpers ----

    private String publishedPie(String name, Map<String, Integer> weights) {
        String pie = me.request().body(body(name, weights)).post("/v1/pies").then().statusCode(201)
            .extract().path("pieId");
        me.request().post("/v1/pies/" + pie + "/publish").then().statusCode(200);
        return pie;
    }

    private static String copy(Trader copier, String account, String pie) {
        return copier.request().body(Map.of("pieId", pie)).post(pieOf(account) + "/copy")
            .then().statusCode(202).extract().path("runId");
    }

    private static JsonPath awaitRun(Trader trader, String runId) {
        return await("run " + runId + " to end").atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(500))
            .until(() -> trader.request().get("/v1/rebalance-runs/" + runId).then().statusCode(200).extract().jsonPath(),
                r -> RUN_ENDED.contains(r.getString("status")));
    }

    private static JsonPath accountPie(Trader trader, String account) {
        return trader.request().get(pieOf(account)).then().statusCode(200).extract().jsonPath();
    }

    private static int orderCount(Trader trader, String account) {
        return trader.request().get("/v1/accounts/" + account + "/orders").then().statusCode(200)
            .extract().jsonPath().getList("$").size();
    }

    private static String pieOf(String account) {
        return "/v1/accounts/" + account + "/pie";
    }

    private static Map<String, Object> body(String name, Map<String, Integer> weights) {
        return Map.of("name", name, "slices", weights.entrySet().stream()
            .map(e -> Map.of("symbol", e.getKey(), "weightPct", e.getValue())).toList());
    }

    /** Uses the same portfolio reference prices as the planner, rather than execution bid/ask quotes. */
    private static void assertWithinOneShare(Trader trader, String account, String symbol, int weightPct) {
        JsonPath snapshot = trader.request().get("/v1/accounts/" + account + "/snapshot")
            .then().statusCode(200).extract().jsonPath();
        BigDecimal total = new BigDecimal(snapshot.getString("totalPortfolioValue"));
        BigDecimal price = new BigDecimal(
            snapshot.getString("positions.find { it.symbol == '" + symbol + "' }.currentPrice"));
        double target = total.doubleValue() * weightPct / 100 / price.doubleValue();
        int held = snapshot.getInt("positions.find { it.symbol == '" + symbol + "' }.quantity");
        assertTrue(Math.abs(held - target) <= 1, symbol + ": held " + held + " but target is " + target);
    }
}
