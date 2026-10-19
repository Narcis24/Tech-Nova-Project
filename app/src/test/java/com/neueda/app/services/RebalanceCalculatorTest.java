package com.neueda.app.services;

import com.neueda.app.enums.PlannedAction;
import com.neueda.app.services.RebalanceCalculator.PlannedLeg;
import com.neueda.app.services.RebalanceCalculator.RebalancePlan;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RebalanceCalculatorTest {

    private final RebalanceCalculator calculator = new RebalanceCalculator(
        decimal("10"), decimal("0.5"), decimal("1"));

    @Test
    void emptyAccount_allNone() {
        RebalancePlan result = plan(Map.of(), Map.of("AAPL", decimal("100")), "0",
            Map.of("AAPL", decimal("50")));

        assertAmount("0", result.totalValue());
        assertEquals(PlannedAction.NONE, leg(result, "AAPL").action());
        assertAmount("0", leg(result, "AAPL").currentPct());
        assertAmount("0", result.cashAfter());
    }

    @Test
    void allCash_buysToTarget() {
        RebalancePlan result = plan(Map.of(),
            Map.of("AAPL", decimal("100"), "MSFT", decimal("300")), "10000",
            Map.of("AAPL", decimal("50"), "MSFT", decimal("30")));

        assertLeg(result, "AAPL", PlannedAction.BUY, 50);
        assertLeg(result, "MSFT", PlannedAction.BUY, 10);
        assertAmount("2000", result.cashAfter());
    }

    @Test
    void overweight_sellsFloor() {
        RebalancePlan result = plan(Map.of("AAPL", 100), Map.of("AAPL", decimal("100")),
            "0", Map.of("AAPL", decimal("50")));

        assertLeg(result, "AAPL", PlannedAction.SELL, 50);
        assertAmount("5000", result.cashAfter());
    }

    @Test
    void underTenDollarsOnceRoundedToWholeShares_none() {
        // a $15 gap at $9 a share is one share, a $9 trade
        RebalancePlan result = plan(Map.of("AAPL", 10), Map.of("AAPL", decimal("9")), "15",
            Map.of("AAPL", decimal("100")));

        assertLeg(result, "AAPL", PlannedAction.NONE, 0);
        assertAmount("15", result.cashAfter());
    }

    @Test
    void buyCappedUnderTenDollarsByTheBudget_none() {
        // the gap is worth 2 shares, but 1% buffer leaves cash for only one $9 share
        RebalancePlan result = plan(Map.of(), Map.of("AAPL", decimal("9")), "18",
            Map.of("AAPL", decimal("100")));

        assertLeg(result, "AAPL", PlannedAction.NONE, 0);
        assertAmount("18", result.cashAfter());
    }

    @Test
    void outsidePie_soldInFull() {
        RebalancePlan result = plan(Map.of("TSLA", 3),
            Map.of("TSLA", decimal("2"), "AAPL", decimal("1")), "0",
            Map.of("AAPL", decimal("100")));

        assertLeg(result, "TSLA", PlannedAction.SELL, 3);
        assertFalse(leg(result, "TSLA").inPie());
    }

    @Test
    void withinDriftOrUnderTenDollars_none() {
        RebalancePlan withinDrift = plan(Map.of("AAPL", 503), Map.of("AAPL", decimal("1")),
            "497", Map.of("AAPL", decimal("50")));
        RebalancePlan underTen = plan(Map.of("AAPL", 91), Map.of("AAPL", decimal("1")),
            "9", Map.of("AAPL", decimal("100")));

        assertLeg(withinDrift, "AAPL", PlannedAction.NONE, 0);
        assertLeg(underTen, "AAPL", PlannedAction.NONE, 0);
    }

    @Test
    void buysCappedByBudget_mostUnderweightFirst() {
        RebalancePlan result = plan(Map.of(),
            Map.of("A", decimal("100"), "B", decimal("100")), "1000",
            Map.of("A", decimal("60"), "B", decimal("40")));

        assertLeg(result, "A", PlannedAction.BUY, 6);
        assertLeg(result, "B", PlannedAction.BUY, 3);
        assertAmount("100", result.cashAfter());
    }

    @Test
    void cashTargetLeftUnspent() {
        RebalancePlan result = plan(Map.of(), Map.of("AAPL", decimal("100")), "10000",
            Map.of("AAPL", decimal("80")));

        assertLeg(result, "AAPL", PlannedAction.BUY, 80);
        assertAmount("2000", result.cashAfter());
    }

    @Test
    void legsOrderedSellBuyNone() {
        RebalancePlan result = plan(Map.of("Z", 10, "A", 10),
            Map.of("Z", decimal("100"), "A", decimal("100"),
                "B", decimal("100"), "C", decimal("100")), "1000",
            Map.of("B", decimal("50"), "C", decimal("0")));

        assertEquals(List.of("A", "Z", "B", "C"),
            result.legs().stream().map(PlannedLeg::symbol).toList());
        assertEquals(List.of(PlannedAction.SELL, PlannedAction.SELL,
            PlannedAction.BUY, PlannedAction.NONE),
            result.legs().stream().map(PlannedLeg::action).toList());
    }

    private RebalancePlan plan(Map<String, Integer> quantities, Map<String, BigDecimal> prices,
                               String cash, Map<String, BigDecimal> targets) {
        return calculator.plan(quantities, prices, decimal(cash), targets);
    }

    private PlannedLeg leg(RebalancePlan plan, String symbol) {
        return plan.legs().stream().filter(item -> item.symbol().equals(symbol)).findFirst().orElseThrow();
    }

    private void assertLeg(RebalancePlan plan, String symbol, PlannedAction action, int quantity) {
        assertEquals(action, leg(plan, symbol).action());
        assertEquals(quantity, leg(plan, symbol).quantity());
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, decimal(expected).compareTo(actual));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
