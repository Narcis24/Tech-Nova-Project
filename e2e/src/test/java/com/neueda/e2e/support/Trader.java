package com.neueda.e2e.support;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * A registered, logged-in user and what they can do over HTTP. The happy-path methods assert
 * success themselves; use {@link #submit} or {@link #request} to check a refusal.
 */
public final class Trader {

    public static final String PASSWORD = "E2e-Password1";

    /** How long an order may take to travel app -> Kafka -> engine -> Kafka -> app and settle. */
    private static final Duration SETTLE = Duration.ofSeconds(30);

    private final String username;
    private final String token;

    private Trader(String username, String token) {
        this.username = username;
        this.token = token;
    }

    /** Registers a new user with auth-service and logs in. */
    public static Trader register() {
        String username = Auth.newUsername();
        return new Trader(username, Auth.registerAndLogin(username).getString("accessToken"));
    }

    public String username() {
        return username;
    }

    public String token() {
        return token;
    }

    /** A request to app carrying this user's token, for calls the methods below do not cover. */
    public RequestSpecification request() {
        return given().baseUri(Stack.appUrl()).auth().oauth2(token).contentType(ContentType.JSON);
    }

    // ---- accounts ----

    /** Opens an account and deposits the given cash; returns its id. */
    public String openAccount(String cash) {
        String accountId = request().body(Map.of("holderName", "E2E Trader")).post("/v1/accounts")
            .then().statusCode(201).extract().path("accountId");
        deposit(accountId, cash);
        return accountId;
    }

    public void deposit(String accountId, String amount) {
        cashMovement(accountId, "deposit", amount).then().statusCode(200);
    }

    public void withdraw(String accountId, String amount) {
        cashMovement(accountId, "withdraw", amount).then().statusCode(200);
    }

    /** POST /v1/accounts/{id}/deposit or /withdraw, accepted or not. */
    public Response cashMovement(String accountId, String direction, String amount) {
        return request().body(Map.of("amount", new BigDecimal(amount)))
            .post("/v1/accounts/" + accountId + "/" + direction);
    }

    public List<String> accountIds() {
        return request().get("/v1/accounts").then().statusCode(200).extract().jsonPath().getList("accountId");
    }

    public BigDecimal balance(String accountId) {
        return new BigDecimal(request().get("/v1/accounts/" + accountId + "/balance").then().statusCode(200)
            .extract().jsonPath().getString("cashBalance"));
    }

    public int sharesHeld(String accountId, String symbol) {
        List<Map<String, Object>> positions = request().get("/v1/accounts/" + accountId + "/positions")
            .then().statusCode(200).extract().jsonPath().getList("$");
        return positions.stream()
            .filter(p -> symbol.equals(p.get("symbol")))
            .mapToInt(p -> ((Number) p.get("quantity")).intValue())
            .sum();
    }

    // ---- orders ----

    /** Submits an order and returns the raw response, accepted or not. */
    public Response submit(OrderRequest order) {
        return request().body(order.toJson()).post("/v1/orders");
    }

    /** Submits an order that app must accept; returns its id. */
    public String place(OrderRequest order) {
        return submit(order).then().statusCode(200).extract().path("orderId");
    }

    /** Places an order and waits for the engine's answer to settle. */
    public Order placeAndSettle(OrderRequest order) {
        return awaitSettled(place(order));
    }

    public Order order(String orderId) {
        return Order.from(request().get("/v1/orders/" + orderId).then().statusCode(200).extract().jsonPath());
    }

    /** Waits until the order leaves PENDING: filled, rejected or cancelled. */
    public Order awaitSettled(String orderId) {
        return await("order " + orderId + " to leave PENDING").atMost(SETTLE).pollInterval(Duration.ofMillis(250))
            .until(() -> order(orderId), o -> !o.isPending());
    }

    /** Checks the order stays PENDING for the whole period, e.g. a LIMIT the market has not reached. */
    public void assertStaysPending(String orderId, Duration period) {
        await("order " + orderId + " to stay PENDING").during(period).atMost(period.multipliedBy(2))
            .until(() -> order(orderId).isPending());
    }

    public Order cancel(String orderId) {
        request().delete("/v1/orders/" + orderId).then().statusCode(200);
        return order(orderId);
    }
}
