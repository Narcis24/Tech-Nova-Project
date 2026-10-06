package com.neueda.e2e.support;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * The quotes the execution engine sees. By default the WireMock stub serves the fixed quotes in
 * wiremock/mappings/alpaca-latest-quotes.json (mirrored below); SLV and every other symbol are
 * never quoted. A test can move one symbol's quote for a while with {@link #moveQuote}.
 */
public final class AlpacaStub {

    public static final BigDecimal AAPL_ASK = new BigDecimal("200.00");
    public static final BigDecimal AAPL_BID = new BigDecimal("199.90");
    public static final BigDecimal MSFT_ASK = new BigDecimal("400.00");
    public static final BigDecimal MSFT_BID = new BigDecimal("399.90");

    private static final String QUOTES_PATH = "/v2/stocks/quotes/latest";

    private AlpacaStub() {
    }

    /**
     * Serves a new quote for one symbol until the returned handle is closed:
     * {@code try (var moved = AlpacaStub.moveQuote("MSFT", "350.00", "349.90")) { ... }}
     */
    public static MovedQuote moveQuote(String symbol, String ask, String bid) {
        Map<String, Map<String, BigDecimal>> quotes = new HashMap<>(Map.of(
            "AAPL", quote(AAPL_ASK, AAPL_BID),
            "MSFT", quote(MSFT_ASK, MSFT_BID)));
        quotes.put(symbol, quote(new BigDecimal(ask), new BigDecimal(bid)));

        // a higher-priority mapping shadows the default one until it is deleted
        Map<String, Object> mapping = Map.of(
            "priority", 1,
            "request", Map.of("method", "GET", "urlPath", QUOTES_PATH),
            "response", Map.of("status", 200, "headers", Map.of("Content-Type", "application/json"),
                "jsonBody", Map.of("quotes", quotes)));
        String id = admin().body(mapping).post("/__admin/mappings").then().statusCode(201).extract().path("id");
        return new MovedQuote(id);
    }

    /** Puts the default quotes back when closed. */
    public static final class MovedQuote implements AutoCloseable {

        private final String mappingId;

        private MovedQuote(String mappingId) {
            this.mappingId = mappingId;
        }

        /**
         * Removes the override, then waits for two more polls of the stub: the engine caches the
         * last quote, so a test starting sooner would still trade at the moved price.
         */
        @Override
        public void close() {
            admin().delete("/__admin/mappings/" + mappingId).then().statusCode(200);
            int served = quoteRequestsServed();
            await("default quotes to reach the engine").atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> quoteRequestsServed() >= served + 2);
        }
    }

    private static Map<String, BigDecimal> quote(BigDecimal ask, BigDecimal bid) {
        return Map.of("ap", ask, "bp", bid);
    }

    private static int quoteRequestsServed() {
        return admin().body(Map.of("method", "GET", "urlPath", QUOTES_PATH))
            .post("/__admin/requests/count").then().statusCode(200).extract().path("count");
    }

    private static RequestSpecification admin() {
        return given().baseUri(Stack.alpacaUrl()).contentType(ContentType.JSON);
    }
}
