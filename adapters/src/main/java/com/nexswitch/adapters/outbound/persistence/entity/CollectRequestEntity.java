package com.nexswitch.adapters.outbound.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "collect_requests")
public class CollectRequestEntity {

    @Id
    @Column(name = "collect_id", nullable = false, updatable = false, length = 19)
    private String collectId;

    @Column(name = "merchant_id", nullable = false, length = 16)
    private String merchantId;

    @Column(name = "payer_vpa", nullable = false, length = 100)
    private String payerVpa;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "npci_txn_id", length = 64)
    private String npciTxnId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CollectRequestEntity() {}

    public String getCollectId()         { return collectId; }
    public String getMerchantId()        { return merchantId; }
    public String getPayerVpa()          { return payerVpa; }
    public BigDecimal getAmount()        { return amount; }
    public String getCurrency()          { return currency; }
    public String getOrderId()           { return orderId; }
    public String getStatus()            { return status; }
    public String getNpciTxnId()         { return npciTxnId; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getExpiresAt()        { return expiresAt; }
    public Instant getUpdatedAt()        { return updatedAt; }

    public void setCollectId(String v)   { this.collectId = v; }
    public void setMerchantId(String v)  { this.merchantId = v; }
    public void setPayerVpa(String v)    { this.payerVpa = v; }
    public void setAmount(BigDecimal v)  { this.amount = v; }
    public void setCurrency(String v)    { this.currency = v; }
    public void setOrderId(String v)     { this.orderId = v; }
    public void setStatus(String v)      { this.status = v; }
    public void setNpciTxnId(String v)   { this.npciTxnId = v; }
    public void setCreatedAt(Instant v)  { this.createdAt = v; }
    public void setExpiresAt(Instant v)  { this.expiresAt = v; }
    public void setUpdatedAt(Instant v)  { this.updatedAt = v; }
}
