package com.nexswitch.domain.model.event;

import com.nexswitch.domain.model.vo.Money;

import java.util.UUID;

// LEARN: DomainEvent — triggers chargeback evidence workflow in chargeback service
public record ChargebackReceivedEvent(
    UUID transactionId,
    Money amount
) {}
