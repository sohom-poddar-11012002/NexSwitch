package com.payments.domain.model.event;

import com.payments.domain.model.vo.Money;

import java.util.UUID;

public record ReversalInitiatedEvent(
    UUID transactionId,
    Money amount
) {}
