package com.payments.adapters.outbound.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "terminals")
public class TerminalEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 8)
    private String id;

    @Column(name = "merchant_id", nullable = false, length = 15)
    private String merchantId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "terminal_key_id", length = 50)
    private String terminalKeyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Getters and setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTerminalKeyId() { return terminalKeyId; }
    public void setTerminalKeyId(String terminalKeyId) { this.terminalKeyId = terminalKeyId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    // ── equals / hashCode on id ───────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TerminalEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
