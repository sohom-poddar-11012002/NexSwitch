package com.nexswitch.reconciliation.scheduler;

import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.ReconciliationResult;
import com.nexswitch.domain.port.inbound.ReconcileUseCase;
import com.nexswitch.domain.port.inbound.ReconciliationCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

// LEARN: @Scheduled cron="0 0 2 * * *" fires at 02:00 UTC every day — runs reconciliation for
//        yesterday's batch so that all transactions captured before end-of-day are included.
//        Spring's task executor calls this on a single thread by default; the job is idempotent
//        because we only transition SETTLEMENT_PENDING → RECONCILED, so double-running is safe.
@Component
public class ScheduledReconciliationTrigger {

    private static final Logger log = LoggerFactory.getLogger(ScheduledReconciliationTrigger.class);

    private static final Set<PaymentNetwork> ALL_NETWORKS = Set.of(PaymentNetwork.values());

    private final ReconcileUseCase reconcileUseCase;

    public ScheduledReconciliationTrigger(ReconcileUseCase reconcileUseCase) {
        this.reconcileUseCase = reconcileUseCase;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runDailyReconciliation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("reconciliation.scheduled.start date={}", yesterday);
        ReconciliationResult result = reconcileUseCase.execute(
                new ReconciliationCommand(yesterday, ALL_NETWORKS));
        switch (result) {
            case ReconciliationResult.Completed c ->
                log.info("reconciliation.scheduled.done date={} matched={} mismatch={} file={}",
                        yesterday, c.matchedCount(), c.mismatchCount(), c.summaryFileKey());
            case ReconciliationResult.Failed f ->
                log.error("reconciliation.scheduled.failed date={} reason={}", yesterday, f.reason());
        }
    }
}
