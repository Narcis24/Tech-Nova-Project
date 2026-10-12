package com.neueda.app.services;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.dtos.RiskLimitResponse;
import com.neueda.app.dtos.RiskWarning;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.LimitSource;
import com.neueda.app.enums.RiskRule;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.models.Instrument;
import com.neueda.app.repositories.InstrumentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Pre-trade exposure check. Looks at the portfolio as it would be after the order and
 * returns a warning for every limit reached. Never blocks an order: the caller decides.
 */
@Service
public class RiskService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PortfolioService portfolioService;
    private final PriceService priceService;
    private final InstrumentRepository instrumentRepository;
    private final RiskLimitService riskLimitService;

    public RiskService(PortfolioService portfolioService, PriceService priceService,
                       InstrumentRepository instrumentRepository, RiskLimitService riskLimitService) {
        this.portfolioService = portfolioService;
        this.priceService = priceService;
        this.instrumentRepository = instrumentRepository;
        this.riskLimitService = riskLimitService;
    }

    public List<RiskWarning> check(PlaceOrderRequest order) {
        List<RiskWarning> warnings = new ArrayList<>();
        String symbol = order.getSymbol();

        // Only buys can raise exposure: a sell lowers its symbol and asset class and raises cash.
        if (!"BUY".equalsIgnoreCase(order.getSide())) {
            return warnings;
        }

        // Step 1: Current portfolio. An unpriced holding is valued at 0 and would skew every percentage.
        PortfolioSnapshotResponse snapshot = portfolioService.getPortfolioSnapshot(order.getAccountId());
        for (PositionResponse position : snapshot.getPositions()) {
            if (position.getCurrentPrice().signum() == 0) {
                return warnings;
            }
        }

        // Step 2: Price of the order. Cash moves at the price paid (the limit price for a limit order);
        // the new shares are valued at market, like every other holding.
        BigDecimal marketPrice;
        try {
            marketPrice = priceService.getCurrentPrice(symbol);
        } catch (PriceNotFoundException e) {
            return warnings;
        }
        BigDecimal pricePaid = order.getPrice() != null ? order.getPrice() : marketPrice;
        BigDecimal quantity = BigDecimal.valueOf(order.getQuantity());

        // Step 3: Portfolio after the order
        Map<String, BigDecimal> valueBySymbol = new HashMap<>();
        for (PositionResponse position : snapshot.getPositions()) {
            valueBySymbol.put(position.getSymbol(), position.getMarketValue());
        }
        BigDecimal orderValue = marketPrice.multiply(quantity);
        valueBySymbol.put(symbol, valueBySymbol.getOrDefault(symbol, BigDecimal.ZERO).add(orderValue));

        BigDecimal cash = snapshot.getCashBalance().subtract(pricePaid.multiply(quantity));
        BigDecimal total = cash;
        for (BigDecimal value : valueBySymbol.values()) {
            total = total.add(value);
        }
        if (total.signum() <= 0) {
            return warnings;
        }

        // Step 4: Compare against the account's limits
        Map<RiskRule, EffectiveLimit> limits = effectiveLimits(order.getAccountId());

        evaluate(warnings, limits.get(RiskRule.SINGLE_INSTRUMENT), RiskRule.SINGLE_INSTRUMENT,
            symbol, valueBySymbol.get(symbol), total, symbol);

        Map<String, AssetClass> assetClassBySymbol = new HashMap<>();
        for (Instrument instrument : instrumentRepository.findAllById(valueBySymbol.keySet())) {
            assetClassBySymbol.put(instrument.getSymbol(), instrument.getAssetClass());
        }
        AssetClass orderClass = assetClassBySymbol.get(symbol);
        if (orderClass != null) {
            BigDecimal classValue = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> entry : valueBySymbol.entrySet()) {
                if (assetClassBySymbol.get(entry.getKey()) == orderClass) {
                    classValue = classValue.add(entry.getValue());
                }
            }
            evaluate(warnings, limits.get(RiskRule.ASSET_CLASS), RiskRule.ASSET_CLASS,
                orderClass.name(), classValue, total, orderClass.name() + " holdings");
        }

        evaluate(warnings, limits.get(RiskRule.CASH_FLOOR), RiskRule.CASH_FLOOR, "CASH", cash, total, "cash");
        return warnings;
    }

    /** The limits in force for the account. Rules the account switched off are left out. */
    private Map<RiskRule, EffectiveLimit> effectiveLimits(String accountId) {
        Map<RiskRule, EffectiveLimit> limits = new HashMap<>();
        for (RiskLimitResponse limit : riskLimitService.getLimits(accountId)) {
            if (limit.isEnabled()) {
                boolean below = "BELOW".equals(limit.getDirection());
                limits.put(limit.getRule(), new EffectiveLimit(limit.getThresholdPct(), below, limit.getSource()));
            }
        }
        return limits;
    }

    /** Adds a warning when the value's share of the total reaches the limit (at or past it). */
    private void evaluate(List<RiskWarning> warnings, EffectiveLimit limit, RiskRule rule,
                          String subject, BigDecimal value, BigDecimal total, String label) {
        if (limit == null) {
            return;
        }
        BigDecimal pct = value.multiply(HUNDRED).divide(total, 4, RoundingMode.HALF_UP);

        boolean reached;
        if (limit.below()) {
            reached = pct.compareTo(limit.thresholdPct()) <= 0;
        } else {
            reached = pct.compareTo(limit.thresholdPct()) >= 0;
        }
        if (!reached) {
            return;
        }

        String shownPct = pct.setScale(1, RoundingMode.HALF_UP).toPlainString();
        String shownLimit = limit.thresholdPct().stripTrailingZeros().toPlainString();
        String limitWord = limit.below() ? "minimum" : "limit";
        String message = "After this order, " + label + " would be " + shownPct
            + "% of your portfolio. Your " + limitWord + " is " + shownLimit + "%.";

        warnings.add(new RiskWarning(rule, subject, pct.setScale(2, RoundingMode.HALF_UP),
            limit.thresholdPct(), limit.source(), message));
    }

    private record EffectiveLimit(BigDecimal thresholdPct, boolean below, LimitSource source) {
    }
}
