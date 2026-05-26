package com.nexswitch.domain.service;

import com.nexswitch.domain.model.ReconciliationResult;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.port.inbound.ReconcileUseCase;
import com.nexswitch.domain.port.inbound.ReconciliationCommand;
import com.nexswitch.domain.port.outbound.TransactionRepository;

import java.util.List;

// LEARN: DomainService — pure Java, no Spring; wired by AdapterConfig. ReconciliationRun
//        is the aggregate but for this sprint we return a lightweight ReconciliationResult
//        to keep the batch step simple.
public class ReconciliationService implements ReconcileUseCase {

    private final TransactionRepository transactionRepository;

    public ReconciliationService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public ReconciliationResult execute(ReconciliationCommand command) {
        // LEARN: reconciliationDate labels the run; the query returns ALL SETTLEMENT_PENDING
        //        regardless of creation time — network filtering controls which schemes are
        //        included. A mismatch here means a scheme was outside this run's scope.
        List<Transaction> pending = transactionRepository.findByStatus(TransactionStatus.SETTLEMENT_PENDING);

        int matched = 0;
        int mismatch = 0;
        for (Transaction txn : pending) {
            if (command.networks().contains(txn.network())) {
                transactionRepository.save(txn.withStatus(TransactionStatus.RECONCILED));
                matched++;
            } else {
                mismatch++;
            }
        }
        return new ReconciliationResult.Completed(matched, mismatch, "recon-" + command.reconciliationDate());
    }
}
