package com.nexswitch.settlement.service;

import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import com.nexswitch.domain.service.TransactionStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// LEARN: ISO 20022 pacs.008 — "Financial Institution Credit Transfer" message used for NEFT/RTGS payouts.
//        Amounts ≥ ₹2,00,000 are eligible for RTGS (real-time); below that use NEFT batch settlement.
//        The actual pacs.008 XML generation is deferred to the bank gateway adapter; we log intent here.
@Service
public class PayoutJobService {

    private static final Logger log = LoggerFactory.getLogger(PayoutJobService.class);

    // LEARN: ₹2,00,000 NEFT/RTGS threshold — RBI mandates RTGS for amounts ≥ ₹2 lakh.
    //        Below this amount, NEFT batches (every 30 min on weekdays) are used.
    private static final BigDecimal RTGS_THRESHOLD = new BigDecimal("200000.00");

    private final TransactionRepository transactionRepository;
    private final TransactionStateMachine stateMachine = new TransactionStateMachine();

    public PayoutJobService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public PayoutJobResult runPayoutJob(LocalDate date) {
        log.info("payout.job.start date={}", date);

        List<Transaction> settled = transactionRepository.findByStatus(TransactionStatus.SETTLED);
        log.info("payout.job.settled_found count={}", settled.size());

        // Group by merchantId and sum amounts
        Map<String, BigDecimal> merchantTotals = new HashMap<>();
        for (Transaction txn : settled) {
            merchantTotals.merge(
                    txn.merchantId().value(),
                    txn.amount().amount(),
                    BigDecimal::add);
        }

        BigDecimal totalPayout = BigDecimal.ZERO;
        int merchantCount = 0;

        for (Map.Entry<String, BigDecimal> entry : merchantTotals.entrySet()) {
            String merchantId = entry.getKey();
            BigDecimal amount  = entry.getValue();

            // LEARN: RTGS vs NEFT — RBI circular: amounts ≥ ₹2,00,000 must go via RTGS.
            //        Log the ISO 20022 pacs.008 that would be generated in production.
            String paymentRail = amount.compareTo(RTGS_THRESHOLD) >= 0 ? "RTGS" : "NEFT";
            log.info("payout.job.merchant_payout merchantId={} amount={} rail={} pacs008=pacs.008.2019.XSD",
                    merchantId, amount, paymentRail);

            merchantCount++;
            totalPayout = totalPayout.add(amount);
        }

        // Transition eligible transactions to PAYOUT_INITIATED
        int transitioned = 0;
        for (Transaction txn : settled) {
            try {
                Transaction initiated = stateMachine.transition(txn, TransactionStatus.PAYOUT_INITIATED);
                transactionRepository.save(initiated);
                transitioned++;
            } catch (Exception e) {
                log.error("payout.job.transition_failed transactionId={} error={}", txn.id(), e.getMessage());
            }
        }

        log.info("payout.job.complete date={} merchants={} total={} transitioned={}",
                date, merchantCount, totalPayout, transitioned);
        return new PayoutJobResult(merchantCount, totalPayout);
    }
}
