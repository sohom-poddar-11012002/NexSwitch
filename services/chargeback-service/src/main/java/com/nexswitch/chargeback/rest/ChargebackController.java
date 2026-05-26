package com.nexswitch.chargeback.rest;

import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/chargebacks")
public class ChargebackController {

    private static final Logger log = LoggerFactory.getLogger(ChargebackController.class);

    private final TransactionRepository transactionRepository;

    public ChargebackController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @PostMapping("/{transactionId}/receive")
    public ResponseEntity<String> receiveChargeback(@PathVariable UUID transactionId) {
        Optional<Transaction> found = transactionRepository.findById(transactionId);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Transaction txn = found.get();
        if (!txn.status().canTransitionTo(TransactionStatus.CHARGEBACK_RECEIVED)) {
            return ResponseEntity.badRequest()
                    .body("cannot receive chargeback in status: " + txn.status());
        }
        transactionRepository.save(txn.withStatus(TransactionStatus.CHARGEBACK_RECEIVED));
        log.info("chargeback.received transactionId={} merchantId={}", transactionId, txn.merchantId().value());
        return ResponseEntity.ok("chargeback received");
    }

    @PostMapping("/{transactionId}/contest")
    public ResponseEntity<String> contestChargeback(@PathVariable UUID transactionId) {
        Optional<Transaction> found = transactionRepository.findById(transactionId);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Transaction txn = found.get();
        if (!txn.status().canTransitionTo(TransactionStatus.CHARGEBACK_CONTESTED)) {
            return ResponseEntity.badRequest()
                    .body("cannot contest chargeback in status: " + txn.status());
        }
        transactionRepository.save(txn.withStatus(TransactionStatus.CHARGEBACK_CONTESTED));
        log.info("chargeback.contested transactionId={}", transactionId);
        return ResponseEntity.ok("chargeback contested");
    }
}
