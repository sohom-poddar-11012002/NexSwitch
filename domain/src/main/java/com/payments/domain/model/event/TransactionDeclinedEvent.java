package com.payments.domain.model.event;

import java.util.UUID;

public record TransactionDeclinedEvent(
    UUID transactionId,
    String responseCode
) {}
