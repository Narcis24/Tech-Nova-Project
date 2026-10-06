package com.neueda.e2e;

import static com.neueda.e2e.support.OrderRequest.marketBuy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;

import com.neueda.e2e.support.Stack;
import com.neueda.e2e.support.Trader;

/**
 * Starts the stack once for all test classes, registers the user the tests act as, and waits
 * until an order really fills. Tests then talk to the services only over HTTP, through
 * {@link Trader}, and open their own accounts so balances start from known values.
 */
abstract class E2ETestBase {

    /** The user every test acts as, unless it registers another. */
    static Trader me;

    @BeforeAll
    static void startStack() {
        if (me != null) {
            return;
        }
        Stack.start();
        me = Trader.register();
        awaitFirstFill();
    }

    /**
     * The engine rejects a MARKET order until its first quote arrives. Retrying one small order
     * until it fills proves every hop works before the tests run.
     */
    private static void awaitFirstFill() {
        String account = me.openAccount("10000.00");
        await("first order to fill").atMost(Duration.ofMinutes(1)).pollInterval(Duration.ofSeconds(1))
            .until(() -> "FILLED".equals(me.placeAndSettle(marketBuy(account, "AAPL", 1)).status()));
    }

    /** Compares amounts by value, so 8000.00 equals 8000.0. */
    static void assertAmount(String expected, BigDecimal actual) {
        assertAmount(new BigDecimal(expected), actual);
    }

    static void assertAmount(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
