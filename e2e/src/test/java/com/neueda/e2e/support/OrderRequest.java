package com.neueda.e2e.support;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The body of POST /v1/orders. Build one with the factories, e.g. {@code marketBuy(account, "AAPL", 10)}. */
public record OrderRequest(String accountId, String symbol, String side, String orderType, int quantity,
        BigDecimal limitPrice, String idempotencyKey) {

    public static OrderRequest marketBuy(String accountId, String symbol, int quantity) {
        return new OrderRequest(accountId, symbol, "BUY", "MARKET", quantity, null, newKey());
    }

    public static OrderRequest marketSell(String accountId, String symbol, int quantity) {
        return new OrderRequest(accountId, symbol, "SELL", "MARKET", quantity, null, newKey());
    }

    public static OrderRequest limitBuy(String accountId, String symbol, int quantity, String limitPrice) {
        return new OrderRequest(accountId, symbol, "BUY", "LIMIT", quantity, new BigDecimal(limitPrice), newKey());
    }

    /** The same order under a given idempotency key. */
    public OrderRequest withIdempotencyKey(String key) {
        return new OrderRequest(accountId, symbol, side, orderType, quantity, limitPrice, key);
    }

    Map<String, Object> toJson() {
        Map<String, Object> body = new HashMap<>(Map.of(
            "accountId", accountId, "symbol", symbol, "side", side, "orderType", orderType,
            "quantity", quantity, "idempotencyKey", idempotencyKey));
        if (limitPrice != null) {
            body.put("price", limitPrice);
        }
        return body;
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}
