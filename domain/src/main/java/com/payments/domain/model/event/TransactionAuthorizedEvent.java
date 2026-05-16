package com.payments.domain.model.event;

import com.payments.domain.model.PaymentNetwork;
import com.payments.domain.model.vo.AuthorizationCode;
import com.payments.domain.model.vo.Money;

import java.util.UUID;

// LEARN: DomainEvent — carries authorizationCode so downstream services don't re-query
public record TransactionAuthorizedEvent(
    UUID transactionId,
    Money amount,
    AuthorizationCode authorizationCode,
    PaymentNetwork network
) {}
