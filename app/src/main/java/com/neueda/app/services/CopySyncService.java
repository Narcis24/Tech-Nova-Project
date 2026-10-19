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
import java.util.Optional;
import java.util.UUID;
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

    /** Starts the latest version when eligible. An active run is checked again when it finishes. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sync(String accountId, UUID pieId) {
        Optional<AccountPie> locked = lockedCopy(accountId, pieId);
        if (locked.isEmpty()) {
            return;
        }
        AccountPie link = locked.get();
        Pie pie = pieRepository.findById(pieId).orElseThrow(() -> new PieNotFoundException("Pie not found: " + pieId));
        if (link.isAutoSync() && pie.isPublished() && link.isBehind(pie)
            && !runRepository.existsByAccountIdAndStatusIn(accountId, RebalanceStatus.ACTIVE)) {
            runService.start(accountId);
        }
    }

    private Optional<AccountPie> lockedCopy(String accountId, UUID pieId) {
        return accountRepository.findByIdForUpdate(accountId)
            .flatMap(account -> accountPieRepository.findById(accountId))
            .filter(link -> link.getMode() == PieLinkMode.COPY && link.getPieId().equals(pieId));
    }
}
