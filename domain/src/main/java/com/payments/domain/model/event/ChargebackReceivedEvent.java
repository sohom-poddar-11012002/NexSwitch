package com.payments.domain.model.event;

import com.payments.domain.model.vo.Money;

import java.util.UUID;

public record ChargebackReceivedEvent(
    UUID transactionId,
    Money amount
) {}
