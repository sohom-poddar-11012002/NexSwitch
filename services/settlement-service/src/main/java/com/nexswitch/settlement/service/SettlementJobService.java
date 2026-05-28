package com.nexswitch.settlement.service;

import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.port.outbound.FileStoragePort;
import com.nexswitch.domain.port.outbound.FileCategory;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import com.nexswitch.domain.service.TransactionStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// LEARN: @Scheduled replaces Spring Batch for leaf services — same steps, no JobRepository overhead.
//        Spring Batch is valuable for restartable jobs with thousands of records; a nightly settlement
//        with <10k transactions fits a simple @Scheduled + service method without extra infrastructure.
@Service
public class SettlementJobService {

    private static final Logger log = LoggerFactory.getLogger(SettlementJobService.class);

    private static final String CSV_HEADER =
            "transactionId,merchantId,amount,currency,network,authCode,rrn\n";

    private final TransactionRepository transactionRepository;
    private final FileStoragePort fileStoragePort;
    // LEARN: TransactionStateMachine is a stateless domain service with no Spring dependencies —
    //        instantiate directly rather than declaring a @Bean, keeping the composition root clean.
    private final TransactionStateMachine stateMachine = new TransactionStateMachine();
    private final ApplicationEventPublisher eventPublisher;

    public SettlementJobService(
            TransactionRepository transactionRepository,
            FileStoragePort fileStoragePort,
            ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.fileStoragePort       = fileStoragePort;
        this.eventPublisher        = eventPublisher;
    }

    /**
     * Multi-step settlement job:
     * 1. Aggregate AUTHORIZED + SETTLEMENT_PENDING transactions for the date.
     * 2. Generate CSV with transaction details.
     * 3. Store CSV via FileStoragePort.
     * 4. Transition each transaction to SETTLED.
     * 5. Publish SettlementCompletedEvent.
     */
    public SettlementJobResult runSettlementJob(LocalDate date) {
        log.info("settlement.job.start date={}", date);

        // ── Step 1: Aggregate ──────────────────────────────────────────────
        List<Transaction> authorizedTxns = transactionRepository.findByStatus(TransactionStatus.AUTHORIZED);
        List<Transaction> pendingTxns    = transactionRepository.findByStatus(TransactionStatus.SETTLEMENT_PENDING);

        List<Transaction> eligible = new ArrayList<>(authorizedTxns.size() + pendingTxns.size());
        eligible.addAll(authorizedTxns);
        eligible.addAll(pendingTxns);

        log.info("settlement.job.eligible authorized={} pending={} total={}",
                authorizedTxns.size(), pendingTxns.size(), eligible.size());

        if (eligible.isEmpty()) {
            log.info("settlement.job.nothing_to_settle date={}", date);
            return new SettlementJobResult(0, BigDecimal.ZERO, "none");
        }

        // ── Step 2: Generate CSV ───────────────────────────────────────────
        StringBuilder csv = new StringBuilder(CSV_HEADER);
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction txn : eligible) {
            csv.append(txn.id()).append(',')
               .append(txn.merchantId().value()).append(',')
               .append(txn.amount().amount().setScale(2, RoundingMode.HALF_UP)).append(',')
               .append(txn.amount().currency().getCurrencyCode()).append(',')
               .append(txn.network().name()).append(',')
               .append(txn.authorizationCode() != null ? txn.authorizationCode().value() : "").append(',')
               .append(txn.arn() != null ? txn.arn().value() : "")
               .append('\n');
            total = total.add(txn.amount().amount());
        }

        // ── Step 3: Store ──────────────────────────────────────────────────
        String fileName = "settlement-" + date + ".csv";
        byte[] csvBytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        fileStoragePort.store(fileName, csvBytes, FileCategory.SETTLEMENT);
        log.info("settlement.job.file_stored fileName={} bytes={}", fileName, csvBytes.length);

        // ── Step 4: Transition to SETTLED ──────────────────────────────────
        // LEARN: multi-hop state machine — AUTHORIZED → CAPTURED → SETTLEMENT_PENDING → RECONCILED → SETTLED.
        //        Batch settlement advances each transaction through only the remaining hops it needs.
        int settledCount = 0;
        for (Transaction txn : eligible) {
            try {
                Transaction current = txn;
                // Advance through intermediate states as needed
                if (current.status() == TransactionStatus.AUTHORIZED) {
                    current = stateMachine.transition(current, TransactionStatus.CAPTURED);
                    transactionRepository.save(current);
                }
                if (current.status() == TransactionStatus.CAPTURED) {
                    current = stateMachine.transition(current, TransactionStatus.SETTLEMENT_PENDING);
                    transactionRepository.save(current);
                }
                if (current.status() == TransactionStatus.SETTLEMENT_PENDING) {
                    current = stateMachine.transition(current, TransactionStatus.RECONCILED);
                    transactionRepository.save(current);
                }
                if (current.status() == TransactionStatus.RECONCILED) {
                    current = stateMachine.transition(current, TransactionStatus.SETTLED);
                    transactionRepository.save(current);
                }
                settledCount++;
            } catch (Exception e) {
                log.error("settlement.job.transition_failed transactionId={} error={}", txn.id(), e.getMessage());
            }
        }

        // ── Step 5: Publish event ──────────────────────────────────────────
        SettlementCompletedEvent event = new SettlementCompletedEvent(date, settledCount, total, fileName);
        eventPublisher.publishEvent(event);

        log.info("settlement.job.complete date={} settled={} total={} file={}",
                date, settledCount, total, fileName);
        return new SettlementJobResult(settledCount, total, fileName);
    }
}
