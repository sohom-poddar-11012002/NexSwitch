package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.AcquirerReferenceNumber;
import com.nexswitch.domain.model.vo.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

// LEARN: RichDomainModel — chargeback has its own lifecycle (responseDeadline, status) independent of Transaction
// LEARN: evidenceDeadline — Visa gives 20 calendar days from chargeback date; Mastercard gives 45 days.
//        Missing this deadline auto-loses the dispute — tracking it in the domain prevents costly write-offs.
public record ChargebackRecord(
    UUID id,
    UUID transactionId,
    AcquirerReferenceNumber arn,
    PaymentNetwork network,
    String reasonCode,
    String reasonDescription,
    LocalDate evidenceDeadline,
    Money amount,
    Money chargebackFee,
    Status status,
    Instant responseDeadline,
    Instant receivedAt
) {
    public ChargebackRecord {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (transactionId == null) throw new IllegalArgumentException("transactionId must not be null");
        if (network == null) throw new IllegalArgumentException("network must not be null");
        if (reasonCode == null || reasonCode.isBlank()) throw new IllegalArgumentException("reasonCode must not be blank");
        if (amount == null) throw new IllegalArgumentException("amount must not be null");
        if (chargebackFee == null) throw new IllegalArgumentException("chargebackFee must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (responseDeadline == null) throw new IllegalArgumentException("responseDeadline must not be null");
        if (receivedAt == null) throw new IllegalArgumentException("receivedAt must not be null");
        // reasonDescription and evidenceDeadline are nullable — may not be present in all network notifications
    }

    public Money totalLiability() {
        Currency currency = amount.currency();
        return amount.add(new Money(chargebackFee.amount(), currency));
    }

    public enum Status {
        RECEIVED, ACCEPTED, CONTESTED, EVIDENCE_SUBMITTED, WON, LOST, WITHDRAWN
    }
}
