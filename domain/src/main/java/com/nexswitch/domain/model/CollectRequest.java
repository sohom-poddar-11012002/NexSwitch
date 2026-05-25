package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;

import java.time.Instant;

// LEARN: UPI Collect (pull) — merchant initiates a debit request to a customer VPA; customer
//        approves on their UPI app. Contrast with QR (push): customer initiates the payment.
//        CollectRequest is persisted in Postgres (not Redis) — no TTL auto-delete; expiresAt
//        is checked in the domain, and a background job transitions PENDING→EXPIRED.
public final class CollectRequest {

    private final String      collectId;
    private final MerchantId  merchantId;
    private final String      payerVpa;
    private final Money       amount;
    private final String      orderId;
    private final Status      status;
    private final Instant     createdAt;
    private final Instant     expiresAt;
    private final String      npciTxnId;

    private CollectRequest(Builder b) {
        if (b.collectId  == null || b.collectId.isBlank())  throw new IllegalArgumentException("collectId required");
        if (b.merchantId == null)  throw new IllegalArgumentException("merchantId required");
        if (b.payerVpa   == null || b.payerVpa.isBlank())   throw new IllegalArgumentException("payerVpa required");
        if (b.amount     == null)  throw new IllegalArgumentException("amount required");
        if (b.orderId    == null || b.orderId.isBlank())     throw new IllegalArgumentException("orderId required");
        if (b.status     == null)  throw new IllegalArgumentException("status required");
        if (b.createdAt  == null)  throw new IllegalArgumentException("createdAt required");
        if (b.expiresAt  == null)  throw new IllegalArgumentException("expiresAt required");
        this.collectId  = b.collectId;
        this.merchantId = b.merchantId;
        this.payerVpa   = b.payerVpa;
        this.amount     = b.amount;
        this.orderId    = b.orderId;
        this.status     = b.status;
        this.createdAt  = b.createdAt;
        this.expiresAt  = b.expiresAt;
        this.npciTxnId  = b.npciTxnId;
    }

    public String      collectId()  { return collectId; }
    public MerchantId  merchantId() { return merchantId; }
    public String      payerVpa()   { return payerVpa; }
    public Money       amount()     { return amount; }
    public String      orderId()    { return orderId; }
    public Status      status()     { return status; }
    public Instant     createdAt()  { return createdAt; }
    public Instant     expiresAt()  { return expiresAt; }
    public String      npciTxnId()  { return npciTxnId; }

    public boolean isExpired()  { return Instant.now().isAfter(expiresAt); }
    public boolean isPending()  { return status == Status.PENDING; }

    public CollectRequest withStatus(Status newStatus) {
        return toBuilder().status(newStatus).build();
    }

    public CollectRequest withNpciTxnId(String id) {
        return toBuilder().npciTxnId(id).build();
    }

    private Builder toBuilder() {
        return new Builder()
                .collectId(collectId).merchantId(merchantId).payerVpa(payerVpa)
                .amount(amount).orderId(orderId).status(status)
                .createdAt(createdAt).expiresAt(expiresAt).npciTxnId(npciTxnId);
    }

    public static Builder builder() { return new Builder(); }

    public enum Status { PENDING, APPROVED, REJECTED, EXPIRED, CANCELLED }

    public static final class Builder {
        private String     collectId;
        private MerchantId merchantId;
        private String     payerVpa;
        private Money      amount;
        private String     orderId;
        private Status     status;
        private Instant    createdAt;
        private Instant    expiresAt;
        private String     npciTxnId;

        public Builder collectId(String v)    { this.collectId  = v; return this; }
        public Builder merchantId(MerchantId v){ this.merchantId = v; return this; }
        public Builder payerVpa(String v)     { this.payerVpa   = v; return this; }
        public Builder amount(Money v)        { this.amount     = v; return this; }
        public Builder orderId(String v)      { this.orderId    = v; return this; }
        public Builder status(Status v)       { this.status     = v; return this; }
        public Builder createdAt(Instant v)   { this.createdAt  = v; return this; }
        public Builder expiresAt(Instant v)   { this.expiresAt  = v; return this; }
        public Builder npciTxnId(String v)    { this.npciTxnId  = v; return this; }

        public CollectRequest build() { return new CollectRequest(this); }
    }
}
