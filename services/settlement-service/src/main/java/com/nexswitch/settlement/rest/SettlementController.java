package com.nexswitch.settlement.rest;

import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// LEARN: @Scheduled + REST trigger — nightly cron handles production; the POST endpoint
//        allows ops to re-run settlement on demand without restarting the service.
@RestController
@RequestMapping("/settlement")
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);

    private final TransactionRepository transactionRepository;

    public SettlementController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @PostMapping("/run")
    public ResponseEntity<String> trigger() {
        int queued = runSettlement();
        return ResponseEntity.ok("settlement queued=" + queued);
    }

    @Scheduled(cron = "0 30 23 * * *")
    public void scheduledRun() {
        runSettlement();
    }

    private int runSettlement() {
        List<Transaction> captured = transactionRepository.findByStatus(TransactionStatus.CAPTURED);
        log.info("settlement.start captured_count={}", captured.size());
        for (Transaction txn : captured) {
            transactionRepository.save(txn.withStatus(TransactionStatus.SETTLEMENT_PENDING));
            log.info("settlement.queued transactionId={} merchantId={}", txn.id(), txn.merchantId().value());
        }
        log.info("settlement.done queued={}", captured.size());
        return captured.size();
    }
}
