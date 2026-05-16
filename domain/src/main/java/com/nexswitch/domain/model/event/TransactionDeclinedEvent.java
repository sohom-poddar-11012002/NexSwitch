package com.nexswitch.domain.model.event;

import java.util.UUID;

// LEARN: DomainEvent — responseCode is the ISO 8583 field 39 value from the network
public record TransactionDeclinedEvent(
    UUID transactionId,
    String responseCode
) {}
