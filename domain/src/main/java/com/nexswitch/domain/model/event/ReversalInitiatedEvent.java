package com.nexswitch.domain.model.event;

import com.nexswitch.domain.model.vo.Money;

import java.util.UUID;

// LEARN: DomainEvent — triggers timeout-reversal saga in payment-switch service
public record ReversalInitiatedEvent(
    UUID transactionId,
    Money amount
) {}
