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
        BigDecimal total = cash;
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            total = total.add(prices.get(entry.getKey()).multiply(BigDecimal.valueOf(entry.getValue())));
        }

        Set<String> symbols = new HashSet<>(quantities.keySet());
        symbols.addAll(targets.keySet());
        Map<String, PlannedLeg> legs = new HashMap<>();
        BigDecimal sellProceeds = BigDecimal.ZERO;
        for (String symbol : symbols) {
            int held = quantities.getOrDefault(symbol, 0);
            BigDecimal price = prices.get(symbol);
            BigDecimal currentValue = price.multiply(BigDecimal.valueOf(held));
            boolean inPie = targets.containsKey(symbol);
            BigDecimal targetPct = targets.getOrDefault(symbol, BigDecimal.ZERO);
            BigDecimal currentPct = total.signum() == 0 ? BigDecimal.ZERO
                : currentValue.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
            BigDecimal targetValue = total.signum() == 0 ? BigDecimal.ZERO
                : targetPct.multiply(total).divide(HUNDRED, 6, RoundingMode.HALF_UP);
            BigDecimal difference = targetValue.subtract(currentValue);
            PlannedAction action = PlannedAction.NONE;
            int quantity = 0;

            if (total.signum() != 0) {
                if (!inPie && held > 0) {
                    action = PlannedAction.SELL;
                    quantity = held;
                } else if (inPie && currentPct.subtract(targetPct).abs().compareTo(minDriftPct) >= 0
                           && difference.abs().compareTo(minTradeValue) >= 0) {
                    quantity = difference.abs().divide(price, 0, RoundingMode.FLOOR).intValueExact();
                    if (quantity > 0) {
                        action = difference.signum() > 0 ? PlannedAction.BUY : PlannedAction.SELL;
                    }
                }
            }

            PlannedLeg leg = new PlannedLeg(symbol, inPie, held, price, currentValue, currentPct,
                targetPct, targetValue, action, quantity);
            legs.put(symbol, leg);
            if (action == PlannedAction.SELL) {
                sellProceeds = sellProceeds.add(price.multiply(BigDecimal.valueOf(quantity)));
            }
        }

        BigDecimal budgetLeft = cash.add(sellProceeds).multiply(HUNDRED.subtract(cashBufferPct))
            .divide(HUNDRED, 6, RoundingMode.HALF_UP);
        List<PlannedLeg> buys = legs.values().stream()
            .filter(leg -> leg.action() == PlannedAction.BUY)
            .sorted(Comparator.comparing((PlannedLeg leg) -> leg.targetValue().subtract(leg.currentValue()))
                .reversed().thenComparing(PlannedLeg::symbol))
            .toList();
        BigDecimal buyCost = BigDecimal.ZERO;
        for (PlannedLeg leg : buys) {
            int affordable = budgetLeft.divide(leg.price(), 0, RoundingMode.FLOOR).intValueExact();
            int quantity = Math.min(leg.quantity(), affordable);
            BigDecimal cost = leg.price().multiply(BigDecimal.valueOf(quantity));
            budgetLeft = budgetLeft.subtract(cost);
            buyCost = buyCost.add(cost);
            legs.put(leg.symbol(), new PlannedLeg(leg.symbol(), leg.inPie(), leg.currentQuantity(),
                leg.price(), leg.currentValue(), leg.currentPct(), leg.targetPct(), leg.targetValue(),
                quantity == 0 ? PlannedAction.NONE : PlannedAction.BUY, quantity));
        }

        List<PlannedLeg> ordered = new ArrayList<>(legs.values());
        ordered.sort(Comparator.comparingInt((PlannedLeg leg) -> switch (leg.action()) {
            case SELL -> 0;
            case BUY -> 1;
            case NONE -> 2;
        }).thenComparing(PlannedLeg::symbol));
        return new RebalancePlan(total, cash, cash.add(sellProceeds).subtract(buyCost), ordered);
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
