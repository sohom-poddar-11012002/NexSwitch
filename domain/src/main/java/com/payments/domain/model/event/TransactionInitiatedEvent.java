package com.payments.domain.model.event;

import com.payments.domain.model.PaymentMethod;
import com.payments.domain.model.PaymentNetwork;
import com.payments.domain.model.vo.MerchantId;
import com.payments.domain.model.vo.Money;

import java.util.UUID;

public record TransactionInitiatedEvent(
    UUID transactionId,
    MerchantId merchantId,
    Money amount,
    PaymentNetwork network,
    PaymentMethod paymentMethod
) {}
