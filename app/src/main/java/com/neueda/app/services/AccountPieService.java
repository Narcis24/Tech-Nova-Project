package com.neueda.app.services;

import com.neueda.app.dtos.AccountPieResponse;
import com.neueda.app.dtos.PreviewLeg;
import com.neueda.app.dtos.RebalancePreviewResponse;
import com.neueda.app.dtos.RebalanceRunResponse;
import com.neueda.app.dtos.SliceDrift;
import com.neueda.app.enums.PieLinkMode;
import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.exceptions.PieNotLinkedException;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.exceptions.RebalanceConflictException;
import com.neueda.app.models.Account;
import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Pie;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.models.RebalanceRun;
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

/**
 * Which pie an account follows, how far it has drifted, and the trades that would fix it. An account follows a pie
 * of its owner's (OWN) or copies a public pie of someone else's (COPY), which it follows as the author changes it.
 */
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
    private final RebalanceRunService rebalanceRunService;

    public AccountPieService(AccountPieRepository accountPieRepository, PieRepository pieRepository,
                             PieService pieService, RebalancePlanner planner, RiskService riskService,
                             AccountRepository accountRepository, RebalanceRunRepository rebalanceRunRepository,
                             RebalanceRunService rebalanceRunService) {
        this.accountPieRepository = accountPieRepository;
        this.pieRepository = pieRepository;
        this.pieService = pieService;
        this.planner = planner;
        this.riskService = riskService;
        this.accountRepository = accountRepository;
        this.rebalanceRunRepository = rebalanceRunRepository;
        this.rebalanceRunService = rebalanceRunService;
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
        Integer version = link.targetVersion(pie);
        if (version == null) {
            throw new PieNotFoundException("Pie not found: " + pie.getPieId());
        }
        return preview(accountId, pie.getPieId(), version);
    }

    /** The preview of copying a public pie into the account. Nothing is linked or placed. */
    public RebalancePreviewResponse previewCopy(String accountId, String owner, UUID pieId) {
        accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId))
            .validateStatus();
        Pie pie = requireCopyable(pieId, owner);
        return preview(accountId, pieId, pie.getLatestVersion());
    }

    /**
     * Links the account to the public pie as a COPY and starts the run to its latest version, in one transaction:
     * if the run can't start, because one is active, orders are pending or the market can't be priced, there is no link.
     */
    @Transactional
    public RebalanceRunResponse copy(String accountId, String owner, UUID pieId) {
        lockAccount(accountId).validateStatus();
        requireCopyable(pieId, owner);
        if (accountPieRepository.findById(accountId)
            .filter(link -> link.getMode() == PieLinkMode.COPY && link.getPieId().equals(pieId)).isPresent()) {
            throw new RebalanceConflictException("This account already copies this pie");
        }
        accountPieRepository.save(AccountPie.copy(accountId, pieId, true));
        return rebalanceRunService.start(accountId);
    }

    /** Turns following the author's new versions on or off. Turning it on starts nothing until the next version. */
    @Transactional
    public AccountPieResponse updateCopySettings(String accountId, boolean autoSync) {
        lockAccount(accountId);
        AccountPie link = requireCopy(accountId);
        link.setAutoSync(autoSync);
        return toResponse(link);
    }

    /**
     * Gives the account a private pie of its own with the slices it last applied from the copied one, and follows that
     * from now on. The holdings stay as they are, so no orders are placed.
     */
    @Transactional
    public AccountPieResponse makeMine(String accountId, String owner) {
        requireNoActiveRun(accountId);
        AccountPie link = requireCopy(accountId);
        if (link.getAppliedVersion() == null) {
            throw new RebalanceConflictException("Nothing from the copied pie has been applied to this account yet");
        }
        Pie mine = pieService.duplicate(owner, requirePie(link.getPieId()), link.getAppliedVersion());
        link.makeOwn(mine.getPieId(), 1);
        return toResponse(link);
    }

    private RebalancePreviewResponse preview(String accountId, UUID pieId, int version) {
        Map<String, BigDecimal> targets = pieService.weightsAt(pieId, version);
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
        return new RebalancePreviewResponse(accountId, pieId, version, plan.totalValue(),
            plan.cashBefore(), plan.cashAfter(), cashTargetPct, legs,
            riskService.checkHoldings(accountId, valuesAfter, plan.cashAfter()));
    }

    private AccountPieResponse toResponse(AccountPie link) {
        return toResponse(link, true);
    }

    /**
     * With tolerateMissingPrices, a missing price leaves the drift empty, with no maximum, instead of failing: an
     * existing link should still show, so it can be unlinked.
     */
    private AccountPieResponse toResponse(AccountPie link, boolean tolerateMissingPrices) {
        Pie pie = requirePie(link.getPieId());
        Integer version = link.targetVersion(pie);
        List<SliceDrift> drift = List.of();
        BigDecimal maxDrift = null;
        try {
            if (version != null) {
                drift = planner.plan(link.getAccountId(), pieService.weightsAt(pie.getPieId(), version)).legs().stream()
                    .filter(PlannedLeg::inPie)
                    .map(leg -> new SliceDrift(leg.symbol(), leg.targetPct(), leg.currentPct(), leg.driftPct()))
                    .toList();
                maxDrift = drift.stream().map(SliceDrift::getDriftPct).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            }
        } catch (PriceNotFoundException e) {
            if (!tolerateMissingPrices) {
                throw e;
            }
        }
        boolean updateAvailable = link.getMode() == PieLinkMode.COPY && pie.isPublished() && link.isBehind(pie);
        RebalanceRun activeRun = rebalanceRunRepository
            .findFirstByAccountIdAndStatusIn(link.getAccountId(), RebalanceStatus.ACTIVE)
            .orElse(null);
        boolean syncPending = updateAvailable && link.isAutoSync() && activeRun != null
            && activeRun.getPieId().equals(pie.getPieId()) && activeRun.getTargetVersion() < pie.getLatestVersion();
        return new AccountPieResponse(link.getAccountId(), pie.getPieId(), pie.getName(), link.getMode().name(),
            link.getAppliedVersion(), version == null ? 0 : version, drift, maxDrift, pie.isPublished(),
            updateAvailable, link.isAutoSync(), syncPending, activeRun == null ? null : activeRun.getRunId());
    }

    /** Under the account's row lock, the same one a run starts under, so a run can't start mid-change. */
    private void requireNoActiveRun(String accountId) {
        lockAccount(accountId);
        if (rebalanceRunRepository.existsByAccountIdAndStatusIn(accountId, RebalanceStatus.ACTIVE)) {
            throw new RebalanceConflictException(
                "A rebalance is running on this account, so its pie can't change until it ends");
        }
    }

    private Account lockAccount(String accountId) {
        return accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    /** A pie of someone else's that is public. Your own is a 409 whatever its visibility; a private one is a 404. */
    private Pie requireCopyable(UUID pieId, String owner) {
        Pie pie = pieRepository.findById(pieId).filter(found -> found.getDeletedAt() == null)
            .orElseThrow(() -> new PieNotFoundException("Pie not found: " + pieId));
        if (pie.getOwnerUsername().equals(owner)) {
            throw new RebalanceConflictException("You can't copy your own pie. Link it to the account instead");
        }
        if (!pie.isPublished()) {
            throw new PieNotFoundException("Pie not found: " + pieId);
        }
        return pie;
    }

    private AccountPie requireCopy(String accountId) {
        AccountPie link = requireLink(accountId);
        if (link.getMode() != PieLinkMode.COPY) {
            throw new RebalanceConflictException("This account isn't copying a pie");
        }
        return link;
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
