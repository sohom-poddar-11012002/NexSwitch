package com.nexswitch.domain.model.event;

import com.nexswitch.domain.model.PaymentMethod;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;

import java.util.UUID;

// LEARN: DomainEvent — published inside Transaction.initiate(); adapter publishes to Kafka after save
public record TransactionInitiatedEvent(
    UUID transactionId,
    MerchantId merchantId,
    Money amount,
    PaymentNetwork network,
    PaymentMethod paymentMethod
) {}
