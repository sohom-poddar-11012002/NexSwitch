package com.nexswitch.domain.model.event;

import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.AuthorizationCode;
import com.nexswitch.domain.model.vo.Money;

import java.util.UUID;

// LEARN: DomainEvent — carries authorizationCode so downstream services don't re-query
public record TransactionAuthorizedEvent(
    UUID transactionId,
    Money amount,
    AuthorizationCode authorizationCode,
    PaymentNetwork network
) {}
