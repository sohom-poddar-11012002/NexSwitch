package com.payments.adapters.outbound.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

// LEARN: JpaEntity — ORM model; separate from MerchantProfile domain record to allow schema evolution
@Entity
@Table(name = "merchants")
public class MerchantEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 15)
    private String id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "mcc", nullable = false, length = 4)
    private String mcc;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "webhook_secret", length = 100)
    private String webhookSecret;

    @Column(name = "mdr_percentage", nullable = false, precision = 5, scale = 4)
    private BigDecimal mdrPercentage;

    @Column(name = "per_txn_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal perTxnLimit;

    @Column(name = "daily_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal dailyLimit;

    @Column(name = "reserve_percentage", nullable = false, precision = 5, scale = 4)
    private BigDecimal reservePercentage;

    @Column(name = "vpa", length = 100)
    private String vpa;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Getters and setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMcc() { return mcc; }
    public void setMcc(String mcc) { this.mcc = mcc; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public BigDecimal getMdrPercentage() { return mdrPercentage; }
    public void setMdrPercentage(BigDecimal mdrPercentage) { this.mdrPercentage = mdrPercentage; }

    public BigDecimal getPerTxnLimit() { return perTxnLimit; }
    public void setPerTxnLimit(BigDecimal perTxnLimit) { this.perTxnLimit = perTxnLimit; }

    public BigDecimal getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(BigDecimal dailyLimit) { this.dailyLimit = dailyLimit; }

    public BigDecimal getReservePercentage() { return reservePercentage; }
    public void setReservePercentage(BigDecimal reservePercentage) { this.reservePercentage = reservePercentage; }

    public String getVpa() { return vpa; }
    public void setVpa(String vpa) { this.vpa = vpa; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    // ── equals / hashCode on id ───────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MerchantEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
