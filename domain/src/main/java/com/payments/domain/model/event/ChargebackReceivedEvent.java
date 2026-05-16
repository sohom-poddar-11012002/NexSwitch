package com.payments.domain.model.event;

import com.payments.domain.model.vo.Money;

import java.util.UUID;

// LEARN: DomainEvent — triggers chargeback evidence workflow in chargeback service
public record ChargebackReceivedEvent(
    UUID transactionId,
    Money amount
) {}
