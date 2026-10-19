package com.neueda.app.services;

import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.events.PieVersionSaved;
import com.neueda.app.events.RebalanceRunFinished;
import com.neueda.app.models.AccountPie;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Moves accounts that copy a pie on when the author saves a new version or an account's run ends. Both happen
 * after the commit that caused them, so a failure here never undoes it. Each account is synced on its own: a
 * failure is logged and the others carry on. The account can still be rebalanced manually.
 */
@Component
@Slf4j
public class CopySyncCoordinator {

    private final CopySyncService syncService;

    public CopySyncCoordinator(CopySyncService syncService) {
        this.syncService = syncService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPieVersionSaved(PieVersionSaved event) {
        List<AccountPie> copies;
        try {
            copies = syncService.autoSyncCopies(event.pieId());
        } catch (RuntimeException e) {
            log.error("Could not list the copies of pie {} to sync", event.pieId(), e);
            return;
        }
        copies.forEach(copy -> sync(copy.getAccountId(), event.pieId()));
    }

    /** Checks for a newer version after a run. Cancellation never starts another run. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunFinished(RebalanceRunFinished event) {
        if (event.status() != RebalanceStatus.CANCELLED) {
            sync(event.accountId(), event.pieId());
        }
    }

    private void sync(String accountId, UUID pieId) {
        try {
            syncService.sync(accountId, pieId);
        } catch (RuntimeException e) {
            log.error("Could not sync account {} to pie {}", accountId, pieId, e);
        }
    }
}
