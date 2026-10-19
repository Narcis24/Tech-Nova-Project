package com.neueda.app.services;

import com.neueda.app.dtos.AccountPieResponse;
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.dtos.PreviewLeg;
import com.neueda.app.dtos.RebalancePreviewResponse;
import com.neueda.app.dtos.SliceDrift;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.exceptions.PieNotLinkedException;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Pie;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.services.RebalanceCalculator.PlannedLeg;
import com.neueda.app.services.RebalanceCalculator.RebalancePlan;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Which pie an account follows, how far it has drifted, and the trades that would fix it. */
@Service
public class AccountPieService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AccountPieRepository accountPieRepository;
    private final PieRepository pieRepository;
    private final PieService pieService;
    private final PortfolioService portfolioService;
    private final PriceService priceService;
    private final RebalanceCalculator rebalanceCalculator;
    private final RiskService riskService;

    public AccountPieService(AccountPieRepository accountPieRepository, PieRepository pieRepository,
                             PieService pieService, PortfolioService portfolioService,
                             PriceService priceService, RebalanceCalculator rebalanceCalculator,
                             RiskService riskService) {
        this.accountPieRepository = accountPieRepository;
        this.pieRepository = pieRepository;
        this.pieService = pieService;
        this.portfolioService = portfolioService;
        this.priceService = priceService;
        this.rebalanceCalculator = rebalanceCalculator;
        this.riskService = riskService;
    }

    public AccountPieResponse link(String accountId, String owner, UUID pieId) {
        pieService.requireOwned(pieId, owner);
        return toResponse(accountPieRepository.save(AccountPie.own(accountId, pieId)));
    }

    public void unlink(String accountId) {
        accountPieRepository.delete(requireLink(accountId));
    }

    public AccountPieResponse get(String accountId) {
        return toResponse(requireLink(accountId));
    }

    public RebalancePreviewResponse preview(String accountId) {
        AccountPie link = requireLink(accountId);
        Pie pie = requirePie(link.getPieId());
        Map<String, BigDecimal> targets = pieService.latestWeights(pie.getPieId());
        PortfolioSnapshotResponse snapshot = portfolioService.getPortfolioSnapshot(accountId);
        RebalancePlan plan = plan(snapshot, targets);

        Map<String, BigDecimal> valuesAfter = new HashMap<>();
        List<PreviewLeg> legs = plan.legs().stream().map(leg -> {
            int delta = switch (leg.action()) {
                case BUY -> leg.quantity();
                case SELL -> -leg.quantity();
                case NONE -> 0;
            };
            valuesAfter.put(leg.symbol(), leg.price().multiply(BigDecimal.valueOf(leg.currentQuantity() + delta)));
            return new PreviewLeg(leg.symbol(), leg.inPie(), leg.currentQuantity(), leg.price(),
                leg.currentValue(), leg.currentPct(), leg.targetPct(), leg.targetValue(), leg.action().name(),
                leg.quantity(), leg.price().multiply(BigDecimal.valueOf(leg.quantity())));
        }).toList();

        BigDecimal cashTargetPct = HUNDRED.subtract(targets.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        return new RebalancePreviewResponse(accountId, pie.getPieId(), pie.getLatestVersion(), plan.totalValue(),
            plan.cashBefore(), plan.cashAfter(), cashTargetPct, legs,
            riskService.checkHoldings(accountId, valuesAfter, plan.cashAfter()));
    }

    private AccountPieResponse toResponse(AccountPie link) {
        Pie pie = requirePie(link.getPieId());
        Map<String, BigDecimal> targets = pieService.latestWeights(pie.getPieId());
        RebalancePlan plan = plan(portfolioService.getPortfolioSnapshot(link.getAccountId()), targets);
        List<SliceDrift> drift = plan.legs().stream()
            .filter(PlannedLeg::inPie)
            .map(leg -> new SliceDrift(leg.symbol(), leg.targetPct(), leg.currentPct(), leg.driftPct()))
            .toList();
        BigDecimal maxDrift = drift.stream().map(SliceDrift::getDriftPct)
            .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        return new AccountPieResponse(link.getAccountId(), pie.getPieId(), pie.getName(), link.getMode().name(),
            link.getAppliedVersion(), pie.getLatestVersion(), drift, maxDrift);
    }

    /** Prices every held and target symbol, then plans. Fails if any of them has no price. */
    private RebalancePlan plan(PortfolioSnapshotResponse snapshot, Map<String, BigDecimal> targets) {
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
        return rebalanceCalculator.plan(quantities, prices, snapshot.getCashBalance(), targets);
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

    private AccountPie requireLink(String accountId) {
        return accountPieRepository.findById(accountId)
            .orElseThrow(() -> new PieNotLinkedException("Account is not following a pie: " + accountId));
    }

    private Pie requirePie(UUID pieId) {
        return pieRepository.findById(pieId)
            .orElseThrow(() -> new PieNotFoundException("Pie not found: " + pieId));
    }
}
