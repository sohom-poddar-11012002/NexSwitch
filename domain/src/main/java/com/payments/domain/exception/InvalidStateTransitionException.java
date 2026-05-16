package com.payments.domain.exception;

import com.payments.domain.model.TransactionStatus;

import java.util.UUID;

// LEARN: StructuredException — typed context fields (transactionId, from, to) make logs machine-parseable
public class InvalidStateTransitionException extends RuntimeException {

    private final UUID transactionId;
    private final TransactionStatus from;
    private final TransactionStatus to;

    public InvalidStateTransitionException(UUID transactionId, TransactionStatus from, TransactionStatus to) {
        super(String.format("Invalid transition for transaction %s: %s → %s", transactionId, from, to));
        this.transactionId = transactionId;
        this.from = from;
        this.to = to;
    }

    public UUID transactionId() { return transactionId; }
    public TransactionStatus from() { return from; }
    public TransactionStatus to() { return to; }
}
