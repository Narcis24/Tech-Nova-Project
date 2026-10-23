package com.neueda.e2e;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import com.neueda.e2e.support.Trader;

class PublishingE2ETest extends E2ETestBase {
    @Test
    void publishingExposesOnlyStrategyDataAndUnpublishingRemovesIt() {
        String pie = create();
        String ownPath = "/v1/pies/" + pie;
        String publicPath = "/v1/pies/public/" + pie;
        Trader other = Trader.register();
        other.request().get(publicPath).then().statusCode(404);
        me.request().post(ownPath + "/publish").then().statusCode(200).body("visibility", equalTo("PUBLIC"));
        String publicBody = other.request().get(publicPath).then().statusCode(200)
            .body("authorUsername", equalTo(me.username()))
            .body("slices", hasSize(1)).body("slices[0].weightPct", equalTo(40.0f))
            .extract().asString();
        assertFalse(publicBody.contains("accountId"), publicBody);
        assertFalse(publicBody.contains("cashBalance"), publicBody);
        assertFalse(publicBody.contains("returnPct"), publicBody);
        assertFalse(publicBody.contains("points"), publicBody);
        other.request().get(ownPath).then().statusCode(404);
        other.request().get("/v1/pies/public").then().statusCode(200)
            .body("find { it.pieId == '" + pie + "' }.name", equalTo("Published strategy"));
        me.request().post(ownPath + "/unpublish").then().statusCode(200).body("visibility", equalTo("PRIVATE"));
        other.request().get(publicPath).then().statusCode(404);
        other.request().get("/v1/pies/public").then().statusCode(200)
            .body("findAll { it.pieId == '" + pie + "' }", hasSize(0));
    }

    @Test
    void publicTemplatesCanBeEditedWithoutAWaitingPeriod() {
        String path = "/v1/pies/" + create();
        me.request().post(path + "/publish").then().statusCode(200);
        me.request().body(body("Published strategy", 50)).put(path).then().statusCode(200).body("version", equalTo(2));
        me.request().body(body("Updated strategy", 60)).put(path).then().statusCode(200).body("version", equalTo(3));
        me.request().body(body("Renamed strategy", 60)).put(path).then().statusCode(200)
            .body("version", equalTo(3)).body("name", equalTo("Renamed strategy"));
    }

    private String create() {
        return me.request().body(body("Published strategy", 40)).post("/v1/pies")
            .then().statusCode(201).extract().path("pieId");
    }

    private static Map<String, Object> body(String name, int weight) {
        return Map.of("name", name, "slices", List.of(Map.of("symbol", "AAPL", "weightPct", weight)));
    }
}
