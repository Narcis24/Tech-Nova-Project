package com.neueda.app.services;

import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.services.RebalanceCalculator.RebalancePlan;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Prices an account's holdings and a pie's slices as they are now, and plans the trades between them. */
@Component
public class RebalancePlanner {

    private final PortfolioService portfolioService;
    private final PriceService priceService;
    private final RebalanceCalculator rebalanceCalculator;

    public RebalancePlanner(PortfolioService portfolioService, PriceService priceService,
                            RebalanceCalculator rebalanceCalculator) {
        this.portfolioService = portfolioService;
        this.priceService = priceService;
        this.rebalanceCalculator = rebalanceCalculator;
    }

    /** Sells and buys to reach the targets, with the buys funded by the planned sells. */
    public RebalancePlan plan(String accountId, Map<String, BigDecimal> targets) {
        return plan(accountId, targets, true);
    }

    /** Same, but the buys are sized from the cash the account holds now. */
    public RebalancePlan planBuys(String accountId, Map<String, BigDecimal> targets) {
        return plan(accountId, targets, false);
    }

    /** Fails if any held or target symbol has no price. */
    private RebalancePlan plan(String accountId, Map<String, BigDecimal> targets, boolean sellsFundBuys) {
        PortfolioSnapshotResponse snapshot = portfolioService.getPortfolioSnapshot(accountId);
        Map<String, Integer> quantities = new HashMap<>();
        Map<String, BigDecimal> prices = new HashMap<>();
        for (PositionResponse position : snapshot.getPositions()) {
            requirePrice(position.getSymbol(), position.getCurrentPrice());
            quantities.put(position.getSymbol(), position.getQuantity());
            prices.put(position.getSymbol(), position.getCurrentPrice());
        }
        for (String symbol : targets.keySet()) {
            if (!prices.containsKey(symbol)) {
                BigDecimal price = currentPrice(symbol);
                requirePrice(symbol, price);
                prices.put(symbol, price);
            }
        }
        return rebalanceCalculator.plan(quantities, prices, snapshot.getCashBalance(), targets, sellsFundBuys);
    }

    private BigDecimal currentPrice(String symbol) {
        try {
            return priceService.getCurrentPrice(symbol);
        } catch (PriceNotFoundException e) {
            return null;
        }
    }

    private static void requirePrice(String symbol, BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new PriceNotFoundException("No price for " + symbol + ", so the rebalance can't be planned");
        }
    }
}
