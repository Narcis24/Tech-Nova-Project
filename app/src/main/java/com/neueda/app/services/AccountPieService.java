package com.neueda.app.services;

import com.neueda.app.dtos.AccountPieResponse;
import com.neueda.app.dtos.PreviewLeg;
import com.neueda.app.dtos.RebalancePreviewResponse;
import com.neueda.app.dtos.SliceDrift;
import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.exceptions.PieNotLinkedException;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.exceptions.RebalanceConflictException;
import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Pie;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.repositories.RebalanceRunRepository;
import com.neueda.app.services.RebalanceCalculator.PlannedLeg;
import com.neueda.app.services.RebalanceCalculator.RebalancePlan;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Which pie an account follows, how far it has drifted, and the trades that would fix it. */
@Service
public class AccountPieService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AccountPieRepository accountPieRepository;
    private final PieRepository pieRepository;
    private final PieService pieService;
    private final RebalancePlanner planner;
    private final RiskService riskService;
    private final AccountRepository accountRepository;
    private final RebalanceRunRepository rebalanceRunRepository;

    public AccountPieService(AccountPieRepository accountPieRepository, PieRepository pieRepository,
                             PieService pieService, RebalancePlanner planner, RiskService riskService,
                             AccountRepository accountRepository, RebalanceRunRepository rebalanceRunRepository) {
        this.accountPieRepository = accountPieRepository;
        this.pieRepository = pieRepository;
        this.pieService = pieService;
        this.planner = planner;
        this.riskService = riskService;
        this.accountRepository = accountRepository;
        this.rebalanceRunRepository = rebalanceRunRepository;
    }

    @Transactional
    public AccountPieResponse link(String accountId, String owner, UUID pieId) {
        requireNoActiveRun(accountId);
        pieService.requireOwned(pieId, owner);
        AccountPie link = AccountPie.own(accountId, pieId);
        AccountPieResponse response = toResponse(link, false);
        accountPieRepository.save(link);
        return response;
    }

    @Transactional
    public void unlink(String accountId) {
        requireNoActiveRun(accountId);
        accountPieRepository.delete(requireLink(accountId));
    }

    public AccountPieResponse get(String accountId) {
        return toResponse(requireLink(accountId), true);
    }

    public RebalancePreviewResponse preview(String accountId) {
        AccountPie link = requireLink(accountId);
        Pie pie = requirePie(link.getPieId());
        Map<String, BigDecimal> targets = pieService.latestWeights(pie.getPieId());
        RebalancePlan plan = planner.plan(accountId, targets);

        Map<String, BigDecimal> valuesAfter = new HashMap<>();
        List<PreviewLeg> legs = plan.legs().stream().map(leg -> {
            int delta = switch (leg.action()) {
                case BUY -> leg.quantity();
                case SELL -> -leg.quantity();
                case NONE -> 0;
            };
            valuesAfter.put(leg.symbol(), leg.price().multiply(BigDecimal.valueOf(leg.currentQuantity() + delta)));
            return new PreviewLeg(leg.symbol(), leg.inPie(), leg.currentQuantity(), leg.price(),
                leg.currentValue(), leg.currentPct(), leg.targetPct(), leg.targetValue(),
                leg.targetValue().subtract(leg.currentValue()), leg.action().name(),
                leg.quantity(), leg.price().multiply(BigDecimal.valueOf(leg.quantity())));
        }).toList();

        BigDecimal cashTargetPct = HUNDRED.subtract(targets.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        return new RebalancePreviewResponse(accountId, pie.getPieId(), pie.getLatestVersion(), plan.totalValue(),
            plan.cashBefore(), plan.cashAfter(), cashTargetPct, legs,
            riskService.checkHoldings(accountId, valuesAfter, plan.cashAfter()));
    }

    /**
     * With tolerateMissingPrices, a missing price leaves the drift empty, with no maximum, instead of failing: an
     * existing link should still show, so it can be unlinked.
     */
    private AccountPieResponse toResponse(AccountPie link, boolean tolerateMissingPrices) {
        Pie pie = requirePie(link.getPieId());
        Map<String, BigDecimal> targets = pieService.latestWeights(pie.getPieId());
        List<SliceDrift> drift = List.of();
        BigDecimal maxDrift = null;
        try {
            RebalancePlan plan = planner.plan(link.getAccountId(), targets);
            drift = plan.legs().stream()
                .filter(PlannedLeg::inPie)
                .map(leg -> new SliceDrift(leg.symbol(), leg.targetPct(), leg.currentPct(), leg.driftPct()))
                .toList();
            maxDrift = drift.stream().map(SliceDrift::getDriftPct).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        } catch (PriceNotFoundException e) {
            if (!tolerateMissingPrices) {
                throw e;
            }
        }
        return new AccountPieResponse(link.getAccountId(), pie.getPieId(), pie.getName(), link.getMode().name(),
            link.getAppliedVersion(), pie.getLatestVersion(), drift, maxDrift);
    }

    /** Under the account's row lock, the same one a run starts under, so a run can't start mid-change. */
    private void requireNoActiveRun(String accountId) {
        accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        if (rebalanceRunRepository.existsByAccountIdAndStatusIn(accountId, RebalanceStatus.ACTIVE)) {
            throw new RebalanceConflictException("A rebalance is running for account " + accountId
                + ", so its pie can't be changed");
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
