package com.nexswitch.adapters.inbound.timeout;

import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.port.inbound.ProcessReversalUseCase;
import com.nexswitch.domain.port.inbound.ReversalCommand;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

// LEARN: ScheduledAdapter — polling is the simplest timeout-detection strategy when a per-request
//        timer is too expensive. fixedDelay means the next run starts 5s after the previous one
//        finishes, avoiding overlapping scans under load (contrast with fixedRate).
//
// LEARN: ISO 8583 §9 mandates a reversal (0400) if no 0110 is received within the terminal timeout.
//        15 seconds is the standard acquire-side timeout; anything still AUTHORIZATION_PENDING
//        after that threshold is a candidate for reversal.
@Component
public class TimeoutReversalMonitor {

    private static final Logger log = LoggerFactory.getLogger(TimeoutReversalMonitor.class);

    static final long TIMEOUT_SECONDS = 15;

    private final TransactionRepository  transactionRepository;
    private final ProcessReversalUseCase processReversalUseCase;

    public TimeoutReversalMonitor(
            TransactionRepository transactionRepository,
            ProcessReversalUseCase processReversalUseCase) {
        this.transactionRepository  = transactionRepository;
        this.processReversalUseCase = processReversalUseCase;
    }

    @Scheduled(fixedDelay = 5_000)
    public void scanAndReverse() {
        Instant threshold = Instant.now().minusSeconds(TIMEOUT_SECONDS);
        List<Transaction> stale = transactionRepository.findAuthorizationPendingOlderThan(threshold);

        for (Transaction txn : stale) {
            log.warn("timeout reversal triggered transactionId={} status={} createdAt={}",
                    txn.id(), txn.status(), txn.createdAt());
            try {
                ReversalCommand cmd = new ReversalCommand(
                        txn.id(),
                        txn.stan(),
                        txn.amount(),
                        txn.amount()
                );
                processReversalUseCase.execute(cmd);
            } catch (Exception e) {
                // LEARN: BestEffort — a failed reversal attempt is logged and re-evaluated on the
                //        next scan; we never throw from @Scheduled or Spring will cancel future runs.
                log.error("reversal failed transactionId={} error={}", txn.id(), e.getMessage(), e);
            }
        }
    }
}
