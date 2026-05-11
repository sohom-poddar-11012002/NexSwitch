package com.payments.domain.model;

import com.payments.domain.model.vo.MerchantId;
import com.payments.domain.model.vo.Money;

import java.time.Instant;

public final class QRSession {

    private final String txnRef;
    private final MerchantId merchantId;
    private final Money amount;
    private final Status status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String npciTxnId;

    private QRSession(Builder builder) {
        if (builder.txnRef == null || builder.txnRef.isBlank())
            throw new IllegalArgumentException("txnRef must not be blank");
        if (builder.merchantId == null) throw new IllegalArgumentException("merchantId must not be null");
        if (builder.amount == null) throw new IllegalArgumentException("amount must not be null");
        if (builder.status == null) throw new IllegalArgumentException("status must not be null");
        if (builder.createdAt == null) throw new IllegalArgumentException("createdAt must not be null");
        if (builder.expiresAt == null) throw new IllegalArgumentException("expiresAt must not be null");

        this.txnRef = builder.txnRef;
        this.merchantId = builder.merchantId;
        this.amount = builder.amount;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.expiresAt = builder.expiresAt;
        this.npciTxnId = builder.npciTxnId;
    }

    public String txnRef() { return txnRef; }
    public MerchantId merchantId() { return merchantId; }
    public Money amount() { return amount; }
    public Status status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public String npciTxnId() { return npciTxnId; }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public QRSession withStatus(Status newStatus) {
        Builder b = toBuilder();
        b.status = newStatus;
        return new QRSession(b);
    }

    public QRSession withNpciTxnId(String id) {
        Builder b = toBuilder();
        b.npciTxnId = id;
        return new QRSession(b);
    }

    private Builder toBuilder() {
        Builder b = new Builder();
        b.txnRef = this.txnRef;
        b.merchantId = this.merchantId;
        b.amount = this.amount;
        b.status = this.status;
        b.createdAt = this.createdAt;
        b.expiresAt = this.expiresAt;
        b.npciTxnId = this.npciTxnId;
        return b;
    }

    public static Builder builder() { return new Builder(); }

    public enum Status { PENDING, COMPLETED, EXPIRED, FAILED }

    public static final class Builder {
        private String txnRef;
        private MerchantId merchantId;
        private Money amount;
        private Status status;
        private Instant createdAt;
        private Instant expiresAt;
        private String npciTxnId;

        public Builder txnRef(String txnRef) { this.txnRef = txnRef; return this; }
        public Builder merchantId(MerchantId merchantId) { this.merchantId = merchantId; return this; }
        public Builder amount(Money amount) { this.amount = amount; return this; }
        public Builder status(Status status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder npciTxnId(String npciTxnId) { this.npciTxnId = npciTxnId; return this; }

        public QRSession build() { return new QRSession(this); }
    }
}
