package com.neueda.app.services;

import com.neueda.app.enums.PlannedAction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RebalanceCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BigDecimal minTradeValue;
    private final BigDecimal minDriftPct;
    private final BigDecimal cashBufferPct;

    public RebalanceCalculator(@Value("${app.pies.min-trade-value:10}") BigDecimal minTradeValue,
                               @Value("${app.pies.min-drift-pct:0.5}") BigDecimal minDriftPct,
                               @Value("${app.pies.cash-buffer-pct:1}") BigDecimal cashBufferPct) {
        this.minTradeValue = minTradeValue;
        this.minDriftPct = minDriftPct;
        this.cashBufferPct = cashBufferPct;
    }

    public RebalancePlan plan(Map<String, Integer> quantities, Map<String, BigDecimal> prices,
                              BigDecimal cash, Map<String, BigDecimal> targets) {
        return plan(quantities, prices, cash, targets, true);
    }

    /**
     * With {@code sellsFundBuys} off, the buys are sized from the cash on hand alone. A run uses that
     * once its sells are done, so a sell that failed doesn't lend money the account never received.
     */
    public RebalancePlan plan(Map<String, Integer> quantities, Map<String, BigDecimal> prices,
                              BigDecimal cash, Map<String, BigDecimal> targets, boolean sellsFundBuys) {
        BigDecimal total = portfolioValue(quantities, prices, cash);

        Set<String> symbols = new HashSet<>(quantities.keySet());
        symbols.addAll(targets.keySet());
        Map<String, PlannedLeg> legs = new HashMap<>();
        BigDecimal sellProceeds = BigDecimal.ZERO;
        for (String symbol : symbols) {
            PlannedLeg leg = legFor(symbol, quantities.getOrDefault(symbol, 0), prices.get(symbol), total, targets);
            legs.put(symbol, leg);
            if (leg.action() == PlannedAction.SELL && sellsFundBuys) {
                sellProceeds = sellProceeds.add(leg.price().multiply(BigDecimal.valueOf(leg.quantity())));
            }
        }

        BigDecimal budget = cash.add(sellProceeds).multiply(HUNDRED.subtract(cashBufferPct))
            .divide(HUNDRED, 6, RoundingMode.HALF_UP);
        BigDecimal buyCost = fitBuysToBudget(legs, budget);
        return new RebalancePlan(total, cash, cash.add(sellProceeds).subtract(buyCost), executionOrder(legs));
    }

    private static BigDecimal portfolioValue(Map<String, Integer> quantities, Map<String, BigDecimal> prices,
                                             BigDecimal cash) {
        BigDecimal total = cash;
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            total = total.add(prices.get(entry.getKey()).multiply(BigDecimal.valueOf(entry.getValue())));
        }
        return total;
    }

    /** Where one symbol stands against its target, and the trade that would close the gap. */
    private PlannedLeg legFor(String symbol, int held, BigDecimal price, BigDecimal total,
                              Map<String, BigDecimal> targets) {
        BigDecimal currentValue = price.multiply(BigDecimal.valueOf(held));
        boolean inPie = targets.containsKey(symbol);
        BigDecimal targetPct = targets.getOrDefault(symbol, BigDecimal.ZERO);
        BigDecimal currentPct = total.signum() == 0 ? BigDecimal.ZERO
            : currentValue.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
        BigDecimal targetValue = total.signum() == 0 ? BigDecimal.ZERO
            : targetPct.multiply(total).divide(HUNDRED, 6, RoundingMode.HALF_UP);
        PlannedAction action = PlannedAction.NONE;
        int quantity = 0;

        if (total.signum() != 0 && !inPie && held > 0) {
            // Not in the pie any more: sell all of it
            action = PlannedAction.SELL;
            quantity = held;
        } else if (total.signum() != 0 && inPie && currentPct.subtract(targetPct).abs().compareTo(minDriftPct) >= 0) {
            BigDecimal difference = targetValue.subtract(currentValue);
            int shares = difference.abs().divide(price, 0, RoundingMode.FLOOR).intValueExact();
            if (worthTrading(price, shares)) {
                action = difference.signum() > 0 ? PlannedAction.BUY : PlannedAction.SELL;
                quantity = shares;
            }
        }
        return new PlannedLeg(symbol, inPie, held, price, currentValue, currentPct, targetPct, targetValue,
            action, quantity);
    }

    /**
     * Cuts the buys down to what the budget covers, the furthest below target first, and returns their
     * cost. A buy that ends up too small to be worth trading is dropped.
     */
    private BigDecimal fitBuysToBudget(Map<String, PlannedLeg> legs, BigDecimal budget) {
        List<PlannedLeg> buys = legs.values().stream()
            .filter(leg -> leg.action() == PlannedAction.BUY)
            .sorted(Comparator.comparing((PlannedLeg leg) -> leg.targetValue().subtract(leg.currentValue()))
                .reversed().thenComparing(PlannedLeg::symbol))
            .toList();
        BigDecimal budgetLeft = budget;
        BigDecimal buyCost = BigDecimal.ZERO;
        for (PlannedLeg leg : buys) {
            int affordable = budgetLeft.divide(leg.price(), 0, RoundingMode.FLOOR).intValueExact();
            int capped = Math.min(leg.quantity(), affordable);
            int quantity = worthTrading(leg.price(), capped) ? capped : 0;
            BigDecimal cost = leg.price().multiply(BigDecimal.valueOf(quantity));
            budgetLeft = budgetLeft.subtract(cost);
            buyCost = buyCost.add(cost);
            legs.put(leg.symbol(), new PlannedLeg(leg.symbol(), leg.inPie(), leg.currentQuantity(),
                leg.price(), leg.currentValue(), leg.currentPct(), leg.targetPct(), leg.targetValue(),
                quantity == 0 ? PlannedAction.NONE : PlannedAction.BUY, quantity));
        }
        return buyCost;
    }

    /** Sells first, so their cash is there for the buys; then by symbol. */
    private static List<PlannedLeg> executionOrder(Map<String, PlannedLeg> legs) {
        List<PlannedLeg> ordered = new ArrayList<>(legs.values());
        ordered.sort(Comparator.comparingInt((PlannedLeg leg) -> switch (leg.action()) {
            case SELL -> 0;
            case BUY -> 1;
            case NONE -> 2;
        }).thenComparing(PlannedLeg::symbol));
        return ordered;
    }

    /** Whole-share trades under the minimum value are skipped, like those that need no shares at all. */
    private boolean worthTrading(BigDecimal price, int shares) {
        return shares > 0 && price.multiply(BigDecimal.valueOf(shares)).compareTo(minTradeValue) >= 0;
    }
    public record RebalancePlan(BigDecimal totalValue, BigDecimal cashBefore, BigDecimal cashAfter,
                                List<PlannedLeg> legs) {
    }

    public record PlannedLeg(String symbol, boolean inPie, int currentQuantity, BigDecimal price,
                             BigDecimal currentValue, BigDecimal currentPct, BigDecimal targetPct,
                             BigDecimal targetValue, PlannedAction action, int quantity) {
        public BigDecimal driftPct() {
            return currentPct.subtract(targetPct).abs();
        }
    }
}
