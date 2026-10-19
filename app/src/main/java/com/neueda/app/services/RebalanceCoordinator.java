package com.neueda.app.services;

import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.events.OrderResolved;
import com.neueda.app.models.RebalanceRun;
import com.neueda.app.repositories.RebalanceRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Moves runs on when orders resolve. Events are only a nudge: the run reads the orders' real status,
 * so a failure here is logged and the run is picked up by the next event or by recovery at startup.
 */
@Component
@Slf4j
public class RebalanceCoordinator {

    private final RebalanceRunService runService;
    private final RebalanceRunRepository runRepository;

    public RebalanceCoordinator(RebalanceRunService runService, RebalanceRunRepository runRepository) {
        this.runService = runService;
        this.runRepository = runRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderResolved(OrderResolved event) {
        try {
            runService.onOrderResolved(event.orderId());
        } catch (RuntimeException e) {
            log.error("Could not advance the rebalance for order {} ({})", event.orderId(), event.status(), e);
        }
    }

    /** Advances runs whose events were missed, e.g. handled while the app was down. */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${app.pies.recovery-delay-ms:30000}")
    public void recover() {
        for (RebalanceRun run : runRepository.findByStatusIn(RebalanceStatus.ACTIVE)) {
            try {
                runService.advance(run.getRunId());
            } catch (RuntimeException e) {
                log.error("Could not recover rebalance run {}", run.getRunId(), e);
            }
        }
    }
}
