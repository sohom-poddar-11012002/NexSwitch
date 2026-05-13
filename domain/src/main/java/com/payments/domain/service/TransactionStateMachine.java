package com.payments.domain.service;

import com.payments.domain.exception.InvalidStateTransitionException;
import com.payments.domain.model.Transaction;
import com.payments.domain.model.TransactionStatus;
import com.payments.domain.model.event.DomainEvent;

import java.util.Objects;

public class TransactionStateMachine {

    public Transaction transition(Transaction transaction, TransactionStatus target) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        Objects.requireNonNull(target, "target must not be null");

        if (!transaction.status().canTransitionTo(target)) {
            throw new InvalidStateTransitionException(transaction.id(), transaction.status(), target);
        }

        Transaction updated = transaction.withStatus(target);
        updated.raiseEvent(DomainEvent.of(
            "transaction." + target.name().toLowerCase(),
            transaction.id().toString(),
            "TRANSACTION",
            target
        ));
        return updated;
    }
}
