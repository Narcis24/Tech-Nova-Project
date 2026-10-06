package com.neueda.e2e.support;

import java.math.BigDecimal;

import io.restassured.path.json.JsonPath;

/**
 * An order as GET /v1/orders/{id} returns it. Once FILLED, price is the execution price;
 * once REJECTED, message is the reason.
 */
public record Order(String id, String status, BigDecimal price, String message) {

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    static Order from(JsonPath json) {
        String price = json.getString("price");
        return new Order(json.getString("orderId"), json.getString("status"),
            price == null ? null : new BigDecimal(price), json.getString("message"));
    }
}
