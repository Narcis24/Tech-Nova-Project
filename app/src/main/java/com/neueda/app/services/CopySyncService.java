package com.neueda.app.services;

import com.neueda.app.enums.PieLinkMode;
import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Pie;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.repositories.RebalanceRunRepository;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brings accounts that copy a pie to its new versions. Every method is one transaction of its own, because
 * they are called after another transaction committed, where joining that one would drop the writes.
 * Each takes the account's row lock first, the one a run is started under, so the account is looked at
 * and changed in turn with its other runs, links and unlinks.
 */
@Service
public class CopySyncService {

    private final AccountRepository accountRepository;
    private final AccountPieRepository accountPieRepository;
    private final PieRepository pieRepository;
    private final RebalanceRunRepository runRepository;
    private final RebalanceRunService runService;

    public CopySyncService(AccountRepository accountRepository, AccountPieRepository accountPieRepository,
                           PieRepository pieRepository, RebalanceRunRepository runRepository,
                           RebalanceRunService runService) {
        this.accountRepository = accountRepository;
        this.accountPieRepository = accountPieRepository;
        this.pieRepository = pieRepository;
        this.runRepository = runRepository;
        this.runService = runService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<AccountPie> autoSyncCopies(UUID pieId) {
        return accountPieRepository.findByPieIdAndModeAndAutoSyncTrue(pieId, PieLinkMode.COPY);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<AccountPie> pending() {
        return accountPieRepository.findBySyncPendingTrue();
    }

    /**
     * Starts a run to the pie's latest version if the account copies it with auto-sync on, the pie is still public
     * and the account is behind. An active run makes it wait: the account is flagged and synced when that run ends.
     * With {@code onlyIfPending} it acts on flagged accounts only. A failure leaves nothing changed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sync(String accountId, UUID pieId, boolean onlyIfPending) {
        Optional<AccountPie> locked = lockedCopy(accountId, pieId);
        if (locked.isEmpty() || onlyIfPending && !locked.get().isSyncPending()) {
            return;
        }
        AccountPie link = locked.get();
        Pie pie = pieRepository.findById(pieId).orElseThrow(() -> new PieNotFoundException("Pie not found: " + pieId));
        if (!link.isAutoSync() || !pie.isPublished() || !link.isBehind(pie)) {
            link.setSyncPending(false);
        } else if (runRepository.existsByAccountIdAndStatusIn(accountId, RebalanceStatus.ACTIVE)) {
            link.setSyncPending(true);
        } else {
            runService.start(accountId);
            // Loaded again: the run's orders may have cleared the persistence context
            accountPieRepository.findById(accountId).ifPresent(started -> started.setSyncPending(false));
        }
    }

    /** Flags the account after a failed sync, so it is tried again by the next event or at startup. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPending(String accountId, UUID pieId) {
        lockedCopy(accountId, pieId).filter(AccountPie::isAutoSync).ifPresent(link -> link.setSyncPending(true));
    }

    /** Drops the flag of a cancelled run's account, unless a newer run is active and the flag may be for it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearPending(String accountId, UUID pieId) {
        lockedCopy(accountId, pieId)
            .filter(link -> !runRepository.existsByAccountIdAndStatusIn(accountId, RebalanceStatus.ACTIVE))
            .ifPresent(link -> link.setSyncPending(false));
    }

    private Optional<AccountPie> lockedCopy(String accountId, UUID pieId) {
        return accountRepository.findByIdForUpdate(accountId)
            .flatMap(account -> accountPieRepository.findById(accountId))
            .filter(link -> link.getMode() == PieLinkMode.COPY && link.getPieId().equals(pieId));
    }
}
