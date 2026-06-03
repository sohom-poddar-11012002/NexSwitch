package com.nexswitch.domain.service;

import com.nexswitch.domain.model.ReconciliationException;
import com.nexswitch.domain.model.ReconciliationResult;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.port.inbound.ReconcileUseCase;
import com.nexswitch.domain.port.inbound.ReconciliationCommand;
import com.nexswitch.domain.port.outbound.AuditPort;
import com.nexswitch.domain.port.outbound.EodFilePort;
import com.nexswitch.domain.port.outbound.ReconciliationExceptionPort;
import com.nexswitch.domain.port.outbound.ReconciliationRunPort;
import com.nexswitch.domain.port.outbound.TransactionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ReconciliationExceptionPort exceptionPort;
    private final ReconciliationRunPort runPort;

    public ReconciliationService(TransactionRepository transactionRepository) {
        this(transactionRepository, new TransactionStateMachine(),
             (a, b, c, d, e, f, g, h) -> {}, null, null, null);
    }

    public ReconciliationService(TransactionRepository transactionRepository,
                                  TransactionStateMachine stateMachine,
                                  AuditPort auditPort,
                                  EodFilePort eodFilePort) {
        this(transactionRepository, stateMachine, auditPort, eodFilePort, null, null);
    }

    public ReconciliationService(TransactionRepository transactionRepository,
                                  TransactionStateMachine stateMachine,
                                  AuditPort auditPort,
                                  EodFilePort eodFilePort,
                                  ReconciliationExceptionPort exceptionPort,
                                  ReconciliationRunPort runPort) {
        this.transactionRepository = transactionRepository;
        this.stateMachine = stateMachine;
        this.auditPort = auditPort;
        this.eodFilePort = eodFilePort;
        this.exceptionPort = exceptionPort;
        this.runPort = runPort;
    }

    @Override
    public ReconciliationResult execute(ReconciliationCommand command) {
        UUID runId = runPort != null
                ? runPort.startRun(command.reconciliationDate())
                : UUID.randomUUID();

        // LEARN: reconciliationDate labels the run; the query returns ALL SETTLEMENT_PENDING
        //        regardless of creation time — network filtering controls which schemes are
        //        included. A mismatch here means a scheme was outside this run's scope.
        List<Transaction> pending = transactionRepository.findByStatus(TransactionStatus.SETTLEMENT_PENDING);

        final Map<UUID, EodFilePort.SettlementRecord> eodIndex;
        if (eodFilePort != null) {
            eodIndex = command.networks().stream()
                    .flatMap(n -> eodFilePort.fetchForDate(command.reconciliationDate(), n).stream())
                    .collect(Collectors.toMap(EodFilePort.SettlementRecord::transactionId,
                            Function.identity(), (a, b) -> a));
        } else {
            eodIndex = Map.of();
        }

        Set<UUID> internalIds = pending.stream().map(Transaction::id).collect(Collectors.toSet());

        int matched = 0, mismatch = 0;

        for (Transaction txn : pending) {
            if (!command.networks().contains(txn.network())) {
                mismatch++;
                continue;
            }

            if (eodFilePort != null) {
                EodFilePort.SettlementRecord record = eodIndex.get(txn.id());
                if (record == null) {
                    mismatch++;
                    writeException(runId, ReconciliationException.ExceptionCategory.MISSING_IN_NETWORK,
                            ReconciliationException.Severity.HIGH,
                            txn.id(), null, txn.amount().amount(), null);
                    continue;
                }
                if (txn.amount().amount().compareTo(record.networkAmount().amount()) != 0) {
                    mismatch++;
                    writeException(runId, ReconciliationException.ExceptionCategory.AMOUNT_MISMATCH,
                            ReconciliationException.Severity.CRIT,
                            txn.id(), record.rrn(), txn.amount().amount(), record.networkAmount().amount());
                    continue;
                }
            }

            String prevStatus = txn.status().name();
            Transaction reconciled = stateMachine.transition(txn, TransactionStatus.RECONCILED);
            transactionRepository.save(reconciled);
            auditPort.record("RECONCILED", "reconciliation-service",
                    txn.id(), txn.id().toString(), "TRANSACTION",
                    prevStatus, TransactionStatus.RECONCILED.name(), null);
            matched++;
        }

        // LEARN: MISSING_IN_SWITCH — bank file contains a transaction our switch never saw.
        //        Most dangerous category: possible duplicate charge or a lost inbound message.
        for (EodFilePort.SettlementRecord record : eodIndex.values()) {
            if (!internalIds.contains(record.transactionId())) {
                mismatch++;
                writeException(runId, ReconciliationException.ExceptionCategory.MISSING_IN_SWITCH,
                        ReconciliationException.Severity.CRIT,
                        null, record.rrn(), null, record.networkAmount().amount());
            }
        }

        int total = matched + mismatch;
        if (runPort != null) {
            runPort.completeRun(runId, total, matched, mismatch);
        }

        return new ReconciliationResult.Completed(matched, mismatch, "recon-" + command.reconciliationDate());
    }

    private void writeException(UUID runId,
                                 ReconciliationException.ExceptionCategory category,
                                 ReconciliationException.Severity severity,
                                 UUID transactionId, String rrn,
                                 BigDecimal ourAmount, BigDecimal networkAmount) {
        if (exceptionPort != null) {
            exceptionPort.save(new ReconciliationException(
                    runId, category, severity, transactionId, rrn, ourAmount, networkAmount));
        }
    }
}
