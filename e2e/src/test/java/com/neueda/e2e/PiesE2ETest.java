package com.neueda.e2e;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.neueda.e2e.support.Trader;

class PiesE2ETest extends E2ETestBase {

    @Test
    void createEditLinkPreviewUnlinkAndDeleteWithoutPlacingOrders() {
        String account = me.openAccount("10000.00");
        String pie = me.request().body(body("My pie", 50, 30)).post("/v1/pies")
            .then().statusCode(201).body("version", equalTo(1)).extract().path("pieId");
        String piePath = "/v1/pies/" + pie;
        String linkPath = "/v1/accounts/" + account + "/pie";

        me.request().body(body("Renamed pie", 50, 30)).put(piePath)
            .then().statusCode(200).body("version", equalTo(1));
        me.request().body(body("Renamed pie", 40, 40)).put(piePath)
            .then().statusCode(200).body("version", equalTo(2));
        me.request().body(Map.of("pieId", pie)).put(linkPath).then().statusCode(200);
        me.request().get(linkPath).then().statusCode(200).body("pieId", equalTo(pie));
        me.request().post(linkPath + "/preview").then().statusCode(200)
            .body("cashTargetPct", equalTo(20.0f))
            .body("legs", hasSize(2))
            .body("legs.find { it.symbol == 'AAPL' }.action", equalTo("BUY"))
            .body("legs.find { it.symbol == 'AAPL' }.quantity", equalTo(20))
            .body("legs.find { it.symbol == 'MSFT' }.quantity", equalTo(10));
        assertAmount("10000.00", me.balance(account));
        assertEquals(0, me.sharesHeld(account, "AAPL"));
        me.request().get("/v1/accounts/" + account + "/orders").then().statusCode(200).body("$", hasSize(0));

        me.request().delete(piePath).then().statusCode(409).body("errorCode", equalTo("PIE_IN_USE"));
        me.request().delete(linkPath).then().statusCode(204);
        me.request().post(linkPath + "/preview").then().statusCode(404).body("errorCode", equalTo("PIE_NOT_LINKED"));
        me.request().delete(piePath).then().statusCode(204);
        me.request().get(piePath).then().statusCode(404);
    }

    @Test
    void otherUsersCannotReadEditDeleteOrLinkThePie() {
        String pie = me.request().body(body("Private", 50, 30)).post("/v1/pies")
            .then().statusCode(201).extract().path("pieId");
        Trader other = Trader.register();
        String account = other.openAccount("100.00");
        String path = "/v1/pies/" + pie;
        other.request().get(path).then().statusCode(404);
        other.request().body(body("Stolen", 50, 30)).put(path).then().statusCode(404);
        other.request().delete(path).then().statusCode(404);
        other.request().body(Map.of("pieId", pie)).put("/v1/accounts/" + account + "/pie")
            .then().statusCode(404).body("errorCode", equalTo("PIE_NOT_FOUND"));
        other.request().get("/v1/pies").then().statusCode(200).body("isEmpty()", is(true));
    }

    private static Map<String, Object> body(String name, int apple, int microsoft) {
        return Map.of("name", name, "slices", List.of(
            Map.of("symbol", "aapl", "weightPct", apple),
            Map.of("symbol", "MSFT", "weightPct", microsoft)));
    }
}
