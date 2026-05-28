package com.nexswitch.domain.service;

import com.nexswitch.domain.exception.InvalidStateTransitionException;
import com.nexswitch.domain.model.*;
import com.nexswitch.domain.port.inbound.ProcessReversalUseCase;
import com.nexswitch.domain.port.inbound.ReversalCommand;
import com.nexswitch.domain.port.outbound.AuditPort;
import com.nexswitch.domain.port.outbound.AuthorizationPort;
import com.nexswitch.domain.port.outbound.TransactionRepository;

import java.util.Optional;

// LEARN: OptimisticConcurrency — instead of SELECT FOR UPDATE, we call initiateReversal() and
//        catch InvalidStateTransitionException. If a concurrent 0110 response already moved the
//        transaction out of AUTHORIZATION_PENDING, the state-machine guard fires and we return
//        AlreadyReversed/Failed safely. The DB row is only written if the guard passes.
public class ReversalService implements ProcessReversalUseCase {

    private final TransactionRepository transactionRepository;
    private final AuthorizationPort     authorizationPort;
    private final TransactionStateMachine stateMachine;
    private final AuditPort             auditPort;

    public ReversalService(
            TransactionRepository transactionRepository,
            AuthorizationPort authorizationPort,
            TransactionStateMachine stateMachine) {
        this(transactionRepository, authorizationPort, stateMachine,
             (a, b, c, d, e, f, g, h) -> {});
    }

    public ReversalService(
            TransactionRepository transactionRepository,
            AuthorizationPort authorizationPort,
            TransactionStateMachine stateMachine,
            AuditPort auditPort) {
        this.transactionRepository = transactionRepository;
        this.authorizationPort     = authorizationPort;
        this.stateMachine          = stateMachine;
        this.auditPort             = auditPort;
    }

    @Override
    public ReversalResult execute(ReversalCommand cmd) {

        Optional<Transaction> opt = transactionRepository.findById(cmd.originalTransactionId());
        if (opt.isEmpty()) {
            return new ReversalResult.Failed("Transaction not found: " + cmd.originalTransactionId());
        }

        Transaction txn = opt.get();

        // Fast idempotency guard — already completed, no work needed
        if (txn.status() == TransactionStatus.REVERSED) {
            return new ReversalResult.AlreadyReversed();
        }

        // Transition to REVERSAL_PENDING — throws if concurrent update already moved the txn
        // LEARN: RaceConditionGuard — the state-machine acts as an optimistic lock; if a concurrent
        //        0110 moved status to AUTHORIZED/DECLINED before this thread executes, canTransitionTo
        //        returns false and we bail out without writing anything to the DB.
        try {
            txn = txn.initiateReversal();
        } catch (InvalidStateTransitionException e) {
            if (txn.status() == TransactionStatus.REVERSED) {
                return new ReversalResult.AlreadyReversed();
            }
            return new ReversalResult.Failed("Irreversible state: " + txn.status());
        }

        txn = transactionRepository.save(txn);  // persist REVERSAL_PENDING

        // Send 0400 advice to the network
        ReversalResult networkResult = authorizationPort.reverse(txn);

        // LEARN: Sealed switch — compiler ensures all ReversalResult variants are handled.
        //        AlreadyReversed from the network is still a success; we move to REVERSED.
        TransactionStatus finalStatus = switch (networkResult) {
            case ReversalResult.Accepted       ignored -> TransactionStatus.REVERSED;
            case ReversalResult.AlreadyReversed ignored -> TransactionStatus.REVERSED;
            case ReversalResult.Failed          ignored -> TransactionStatus.UNKNOWN;
        };

        String prevStatus = txn.status().name();
        txn = stateMachine.transition(txn, finalStatus);
        transactionRepository.save(txn);
        auditPort.record("TRANSACTION_REVERSED", "reversal-service",
                txn.id(), txn.id().toString(), "TRANSACTION", prevStatus, finalStatus.name(), null);

        return networkResult;
    }
}
