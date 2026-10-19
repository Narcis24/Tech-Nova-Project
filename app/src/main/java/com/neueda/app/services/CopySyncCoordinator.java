package com.neueda.app.services;

import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.events.PieVersionSaved;
import com.neueda.app.events.RebalanceRunFinished;
import com.neueda.app.models.AccountPie;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Moves accounts that copy a pie on when the author saves a new version or an account's run ends. Both happen
 * after the commit that caused them, so a failure here never undoes it. Each account is synced on its own: a
 * failure is logged, the account is flagged for a later try and the others carry on.
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
        copies.forEach(copy -> sync(copy.getAccountId(), event.pieId(), false));
    }

    /** Syncs an account flagged while its run was active. A cancelled run is never followed by a sync. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunFinished(RebalanceRunFinished event) {
        if (event.status() != RebalanceStatus.CANCELLED) {
            sync(event.accountId(), event.pieId(), true);
            return;
        }
        try {
            syncService.clearPending(event.accountId(), event.pieId());
        } catch (RuntimeException e) {
            log.error("Could not clear the sync of account {} after its run was cancelled", event.accountId(), e);
        }
    }

    /** Syncs accounts flagged before the app stopped or whose sync failed. */
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        try {
            syncService.pending().forEach(link -> sync(link.getAccountId(), link.getPieId(), true));
        } catch (RuntimeException e) {
            log.error("Could not list the accounts waiting for a sync", e);
        }
    }

    private void sync(String accountId, UUID pieId, boolean onlyIfPending) {
        try {
            syncService.sync(accountId, pieId, onlyIfPending);
        } catch (RuntimeException e) {
            log.error("Could not sync account {} to pie {}, flagging it for a retry", accountId, pieId, e);
            try {
                syncService.markPending(accountId, pieId);
            } catch (RuntimeException flagFailure) {
                log.error("Could not flag account {} for a retry", accountId, flagFailure);
            }
        }
    }
}
