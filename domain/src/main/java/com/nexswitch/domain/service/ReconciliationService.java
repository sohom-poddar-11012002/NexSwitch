package com.nexswitch.domain.service;

import com.nexswitch.domain.model.ReconciliationResult;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.port.inbound.ReconcileUseCase;
import com.nexswitch.domain.port.inbound.ReconciliationCommand;
import com.nexswitch.domain.port.outbound.AuditPort;
import com.nexswitch.domain.port.outbound.EodFilePort;
import com.nexswitch.domain.port.outbound.TransactionRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// LEARN: DomainService — pure Java, no Spring; wired by AdapterConfig. ReconciliationRun
//        is the aggregate but for this sprint we return a lightweight ReconciliationResult
//        to keep the batch step simple.
public class ReconciliationService implements ReconcileUseCase {

    private final TransactionRepository transactionRepository;
    private final TransactionStateMachine stateMachine;
    private final AuditPort auditPort;
    private final EodFilePort eodFilePort;

    public ReconciliationService(TransactionRepository transactionRepository) {
        this(transactionRepository, new TransactionStateMachine(),
             (a, b, c, d, e, f, g, h) -> {}, null);
    }

    public ReconciliationService(TransactionRepository transactionRepository,
                                  TransactionStateMachine stateMachine,
                                  AuditPort auditPort,
                                  EodFilePort eodFilePort) {
        this.transactionRepository = transactionRepository;
        this.stateMachine = stateMachine;
        this.auditPort = auditPort;
        this.eodFilePort = eodFilePort;
    }

    @Override
    public ReconciliationResult execute(ReconciliationCommand command) {
        // LEARN: reconciliationDate labels the run; the query returns ALL SETTLEMENT_PENDING
        //        regardless of creation time — network filtering controls which schemes are
        //        included. A mismatch here means a scheme was outside this run's scope.
        List<Transaction> pending = transactionRepository.findByStatus(TransactionStatus.SETTLEMENT_PENDING);

        // When an EOD file port is wired, build a lookup map of network-confirmed transactions
        // for three-way diff (DB vs network vs expected). Absent port = DB-only sweep.
        final Map<UUID, EodFilePort.SettlementRecord> eodIndex;
        if (eodFilePort != null) {
            eodIndex = command.networks().stream()
                    .flatMap(n -> eodFilePort.fetchForDate(command.reconciliationDate(), n).stream())
                    .collect(Collectors.toMap(EodFilePort.SettlementRecord::transactionId, Function.identity(),
                            (a, b) -> a));
        } else {
            eodIndex = Map.of();
        }

        int matched = 0;
        int mismatch = 0;
        for (Transaction txn : pending) {
            if (!command.networks().contains(txn.network())) {
                mismatch++;
                continue;
            }
            // When eodIndex is populated, only reconcile transactions confirmed by the network file
            if (!eodIndex.isEmpty() && !eodIndex.containsKey(txn.id())) {
                mismatch++;
                continue;
            }
            String prevStatus = txn.status().name();
            Transaction reconciled = stateMachine.transition(txn, TransactionStatus.RECONCILED);
            transactionRepository.save(reconciled);
            auditPort.record("RECONCILED", "reconciliation-service",
                    txn.id(), txn.id().toString(), "TRANSACTION",
                    prevStatus, TransactionStatus.RECONCILED.name(), null);
            matched++;
        }
        return new ReconciliationResult.Completed(matched, mismatch, "recon-" + command.reconciliationDate());
    }
}
