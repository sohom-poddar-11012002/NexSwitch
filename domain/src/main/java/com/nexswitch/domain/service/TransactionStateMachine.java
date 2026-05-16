package com.nexswitch.domain.service;

import com.nexswitch.domain.exception.InvalidStateTransitionException;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.model.event.DomainEvent;

import java.util.Objects;

// LEARN: StateMachine — single entry point for all state transitions; raises domain event on every change
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
