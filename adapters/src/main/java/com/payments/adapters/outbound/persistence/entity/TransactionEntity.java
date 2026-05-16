package com.payments.adapters.outbound.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// LEARN: OptimisticLocking — @Version field; JPA increments on every UPDATE, throws on stale read
@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "stan", nullable = false, length = 6)
    private String stan;

    @Column(name = "rrn", length = 12)
    private String rrn;

    @Column(name = "arn", length = 23)
    private String arn;

    @Column(name = "terminal_id", nullable = false, length = 8)
    private String terminalId;

    @Column(name = "merchant_id", nullable = false, length = 15)
    private String merchantId;

    @Column(name = "pan_hash", nullable = false, length = 64)
    private String panHash;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "network", length = 20)
    private String network;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "authorization_code", length = 6)
    private String authorizationCode;

    @Column(name = "response_code", length = 2)
    private String responseCode;

    @Column(name = "risk_score", length = 10)
    private String riskScore;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "npci_txn_id", length = 50)
    private String npciTxnId;

    @Column(name = "qr_txn_ref", length = 50)
    private String qrTxnRef;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "upstream_request_at")
    private Instant upstreamRequestAt;

    @Column(name = "upstream_response_at")
    private Instant upstreamResponseAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "paid_out_at")
    private Instant paidOutAt;

    // ── Getters and setters ────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }

    public String getRrn() { return rrn; }
    public void setRrn(String rrn) { this.rrn = rrn; }

    public String getArn() { return arn; }
    public void setArn(String arn) { this.arn = arn; }

    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getPanHash() { return panHash; }
    public void setPanHash(String panHash) { this.panHash = panHash; }

    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }

    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAuthorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }

    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }

    public String getRiskScore() { return riskScore; }
    public void setRiskScore(String riskScore) { this.riskScore = riskScore; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getNpciTxnId() { return npciTxnId; }
    public void setNpciTxnId(String npciTxnId) { this.npciTxnId = npciTxnId; }

    public String getQrTxnRef() { return qrTxnRef; }
    public void setQrTxnRef(String qrTxnRef) { this.qrTxnRef = qrTxnRef; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getUpstreamRequestAt() { return upstreamRequestAt; }
    public void setUpstreamRequestAt(Instant upstreamRequestAt) { this.upstreamRequestAt = upstreamRequestAt; }

    public Instant getUpstreamResponseAt() { return upstreamResponseAt; }
    public void setUpstreamResponseAt(Instant upstreamResponseAt) { this.upstreamResponseAt = upstreamResponseAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }

    public Instant getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(Instant reconciledAt) { this.reconciledAt = reconciledAt; }

    public Instant getPaidOutAt() { return paidOutAt; }
    public void setPaidOutAt(Instant paidOutAt) { this.paidOutAt = paidOutAt; }

    // ── equals / hashCode on id (JPA best practice) ────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
