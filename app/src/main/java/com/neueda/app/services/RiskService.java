package com.neueda.app.services;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.dtos.RiskLimitResponse;
import com.neueda.app.dtos.RiskWarning;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.LimitSource;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderType;
import com.neueda.app.enums.RiskRule;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.models.Instrument;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.services.OrderValidator.ValidatedOrder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final OrderValidator orderValidator;

    public RiskService(PortfolioService portfolioService, PriceService priceService,
                       InstrumentRepository instrumentRepository, RiskLimitService riskLimitService,
                       OrderValidator orderValidator) {
        this.portfolioService = portfolioService;
        this.priceService = priceService;
        this.instrumentRepository = instrumentRepository;
        this.riskLimitService = riskLimitService;
        this.orderValidator = orderValidator;
    }

    public List<RiskWarning> check(PlaceOrderRequest order) {
        List<RiskWarning> warnings = new ArrayList<>();
        String symbol = order.getSymbol();

        // The same checks as placing the order, so an order /orders would refuse is refused here too
        ValidatedOrder valid = orderValidator.validate(order);

        // Only buys can raise exposure: a sell lowers its symbol and asset class and raises cash.
        if (valid.side() != OrderSide.BUY) {
            return warnings;
        }

        // Step 1: Current portfolio. An unpriced holding is valued at 0 and would skew every percentage.
        PortfolioSnapshotResponse snapshot = portfolioService.getPortfolioSnapshot(order.getAccountId());
        if (hasUnpricedHolding(snapshot)) {
            return warnings;
        }

        // Step 2: Price of the order. Cash moves at the price paid (the limit price for a limit order);
        // the new shares are valued at market, like every other holding.
        BigDecimal pricePaid = valid.price();
        BigDecimal marketPrice = marketPrice(valid, symbol);
        if (marketPrice == null) {
            return warnings;
        }
        BigDecimal quantity = BigDecimal.valueOf(order.getQuantity());

        // Step 3: Portfolio after the order
        Map<String, BigDecimal> valueBySymbol = valuesAfterOrder(snapshot, symbol, marketPrice.multiply(quantity));
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

        String prefix = "After this order, ";
        evaluate(warnings, prefix, limits.get(RiskRule.SINGLE_INSTRUMENT), RiskRule.SINGLE_INSTRUMENT,
            symbol, valueBySymbol.get(symbol), total, symbol);
        checkAssetClass(warnings, prefix, limits.get(RiskRule.ASSET_CLASS), List.of(symbol), valueBySymbol, total);
        evaluate(warnings, prefix, limits.get(RiskRule.CASH_FLOOR), RiskRule.CASH_FLOOR, "CASH", cash, total, "cash");
        return warnings;
    }

    /** Checks every holding and asset class of a whole portfolio, e.g. the one a rebalance would leave. */
    public List<RiskWarning> checkHoldings(String accountId, Map<String, BigDecimal> valueBySymbol, BigDecimal cash) {
        List<RiskWarning> warnings = new ArrayList<>();
        BigDecimal total = cash;
        for (BigDecimal value : valueBySymbol.values()) {
            total = total.add(value);
        }
        if (total.signum() <= 0) {
            return warnings;
        }

        Map<RiskRule, EffectiveLimit> limits = effectiveLimits(accountId);
        String prefix = "After rebalancing, ";
        for (Map.Entry<String, BigDecimal> entry : valueBySymbol.entrySet()) {
            evaluate(warnings, prefix, limits.get(RiskRule.SINGLE_INSTRUMENT), RiskRule.SINGLE_INSTRUMENT,
                entry.getKey(), entry.getValue(), total, entry.getKey());
        }
        checkAssetClass(warnings, prefix, limits.get(RiskRule.ASSET_CLASS), valueBySymbol.keySet(), valueBySymbol, total);
        evaluate(warnings, prefix, limits.get(RiskRule.CASH_FLOOR), RiskRule.CASH_FLOOR, "CASH", cash, total, "cash");
        return warnings;
    }

    private boolean hasUnpricedHolding(PortfolioSnapshotResponse snapshot) {
        for (PositionResponse position : snapshot.getPositions()) {
            if (position.getCurrentPrice().signum() == 0) {
                return true;
            }
        }
        return false;
    }

    /** The order's price for a market order, today's price otherwise; null when there is none. */
    private BigDecimal marketPrice(ValidatedOrder valid, String symbol) {
        if (valid.orderType() == OrderType.MARKET) {
            return valid.price();
        }
        try {
            return priceService.getCurrentPrice(symbol);
        } catch (PriceNotFoundException e) {
            return null;
        }
    }

    /** Market value per symbol once the order's shares are added. */
    private Map<String, BigDecimal> valuesAfterOrder(PortfolioSnapshotResponse snapshot, String symbol, BigDecimal orderValue) {
        Map<String, BigDecimal> valueBySymbol = new HashMap<>();
        for (PositionResponse position : snapshot.getPositions()) {
            valueBySymbol.put(position.getSymbol(), position.getMarketValue());
        }
        valueBySymbol.put(symbol, valueBySymbol.getOrDefault(symbol, BigDecimal.ZERO).add(orderValue));
        return valueBySymbol;
    }

    /** Warns for each asset class of the given symbols that reaches its limit. */
    private void checkAssetClass(List<RiskWarning> warnings, String prefix, EffectiveLimit limit,
                                 Collection<String> symbols, Map<String, BigDecimal> valueBySymbol, BigDecimal total) {
        Map<String, AssetClass> assetClassBySymbol = new HashMap<>();
        for (Instrument instrument : instrumentRepository.findAllById(valueBySymbol.keySet())) {
            assetClassBySymbol.put(instrument.getSymbol(), instrument.getAssetClass());
        }
        Map<AssetClass, BigDecimal> valueByClass = new EnumMap<>(AssetClass.class);
        for (Map.Entry<String, BigDecimal> entry : valueBySymbol.entrySet()) {
            AssetClass assetClass = assetClassBySymbol.get(entry.getKey());
            if (assetClass != null) {
                valueByClass.merge(assetClass, entry.getValue(), BigDecimal::add);
            }
        }
        Set<AssetClass> classes = EnumSet.noneOf(AssetClass.class);
        for (String symbol : symbols) {
            AssetClass assetClass = assetClassBySymbol.get(symbol);
            if (assetClass != null) {
                classes.add(assetClass);
            }
        }
        for (AssetClass assetClass : classes) {
            evaluate(warnings, prefix, limit, RiskRule.ASSET_CLASS, assetClass.name(), valueByClass.get(assetClass),
                total, assetClass.name() + " holdings");
        }
    }

    /** The limits in force for the account. Rules the account switched off are left out. */
    private Map<RiskRule, EffectiveLimit> effectiveLimits(String accountId) {
        Map<RiskRule, EffectiveLimit> limits = new EnumMap<>(RiskRule.class);
        for (RiskLimitResponse limit : riskLimitService.getLimits(accountId)) {
            if (limit.isEnabled()) {
                boolean below = "BELOW".equals(limit.getDirection());
                limits.put(limit.getRule(), new EffectiveLimit(limit.getThresholdPct(), below, limit.getSource()));
            }
        }
        return limits;
    }

    /** Adds a warning when the value's share of the total reaches the limit (at or past it). */
    private void evaluate(List<RiskWarning> warnings, String prefix, EffectiveLimit limit, RiskRule rule,
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
        String message = prefix + label + " would be " + shownPct
            + "% of your portfolio. Your " + limitWord + " is " + shownLimit + "%.";

        warnings.add(new RiskWarning(rule, subject, pct.setScale(2, RoundingMode.HALF_UP),
            limit.thresholdPct(), limit.source(), message));
    }

    private record EffectiveLimit(BigDecimal thresholdPct, boolean below, LimitSource source) {
    }
}
