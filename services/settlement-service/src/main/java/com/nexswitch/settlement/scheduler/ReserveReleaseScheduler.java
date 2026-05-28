package com.nexswitch.settlement.scheduler;

import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import com.nexswitch.domain.service.TransactionStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// LEARN: Reserve account — acquirers hold a percentage of merchant settlement for a rolling window
//        (90 days is common) as protection against chargebacks and fraud after payout.
//        After 90 days, the reserve is released to the merchant's settlement account.
@Component
public class ReserveReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReserveReleaseScheduler.class);

    private static final int RESERVE_DAYS = 90;

    private final TransactionRepository transactionRepository;
    private final TransactionStateMachine stateMachine = new TransactionStateMachine();
    private final Clock clock;

    public ReserveReleaseScheduler(TransactionRepository transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock                 = clock;
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Kolkata")
    public void releaseExpiredReserves() {
        Instant threshold = Instant.now(clock).minus(RESERVE_DAYS, ChronoUnit.DAYS);
        log.info("reserve_release.start threshold={}", threshold);

        // Find PAYOUT_INITIATED transactions older than 90 days (reserve window elapsed)
        List<Transaction> eligible = transactionRepository.findByStatus(TransactionStatus.PAYOUT_INITIATED)
                .stream()
                .filter(txn -> txn.createdAt().isBefore(threshold))
                .toList();

        log.info("reserve_release.eligible count={}", eligible.size());

        Map<String, BigDecimal> merchantReleases = new HashMap<>();
        int released = 0;

        for (Transaction txn : eligible) {
            try {
                Transaction completed = stateMachine.transition(txn, TransactionStatus.RESERVE_RELEASED);
                transactionRepository.save(completed);
                merchantReleases.merge(
                        txn.merchantId().value(),
                        txn.amount().amount(),
                        BigDecimal::add);
                released++;
            } catch (Exception e) {
                log.error("reserve_release.transition_failed transactionId={} error={}", txn.id(), e.getMessage());
            }
        }

        // Log per-merchant release amounts
        for (Map.Entry<String, BigDecimal> entry : merchantReleases.entrySet()) {
            log.info("reserve_release.merchant merchantId={} releasedAmount={}", entry.getKey(), entry.getValue());
        }

        log.info("reserve_release.done released={} merchants={}", released, merchantReleases.size());
    }
}
