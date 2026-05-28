package com.nexswitch.domain.model;

import com.nexswitch.domain.exception.InvalidStateTransitionException;
import com.nexswitch.domain.model.event.*;
import com.nexswitch.domain.model.vo.*;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// LEARN: AggregateRoot — static factory (initiate), guard+throw, raises DomainEvents; no setters
public final class Transaction {

    private final UUID id;
    private final MerchantId merchantId;
    private final TerminalId terminalId;
    private final Money amount;
    private final PaymentNetwork network;
    private final PaymentMethod paymentMethod;
    private final PanHash panHash;
    private final SystemTraceAuditNumber stan;
    private final TransactionStatus status;
    private final AuthorizationCode authorizationCode;
    private final AcquirerReferenceNumber arn;
    private final String responseCode;
    private final String cardLast4;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<DomainEvent<?>> domainEvents;

    private Transaction(Builder builder) {
        if (builder.id == null) throw new IllegalArgumentException("id must not be null");
        if (builder.merchantId == null) throw new IllegalArgumentException("merchantId must not be null");
        if (builder.terminalId == null) throw new IllegalArgumentException("terminalId must not be null");
        if (builder.amount == null) throw new IllegalArgumentException("amount must not be null");
        if (builder.network == null) throw new IllegalArgumentException("network must not be null");
        if (builder.paymentMethod == null) throw new IllegalArgumentException("paymentMethod must not be null");
        if (builder.panHash == null) throw new IllegalArgumentException("panHash must not be null");
        if (builder.stan == null) throw new IllegalArgumentException("stan must not be null");
        if (builder.status == null) throw new IllegalArgumentException("status must not be null");
        if (builder.createdAt == null) throw new IllegalArgumentException("createdAt must not be null");

        this.id = builder.id;
        this.merchantId = builder.merchantId;
        this.terminalId = builder.terminalId;
        this.amount = builder.amount;
        this.network = builder.network;
        this.paymentMethod = builder.paymentMethod;
        this.panHash = builder.panHash;
        this.stan = builder.stan;
        this.status = builder.status;
        this.authorizationCode = builder.authorizationCode;
        this.arn = builder.arn;
        this.responseCode = builder.responseCode;
        this.cardLast4 = builder.cardLast4;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : builder.createdAt;
        this.domainEvents = new ArrayList<>(builder.domainEvents);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID id() { return id; }
    public MerchantId merchantId() { return merchantId; }
    public TerminalId terminalId() { return terminalId; }
    public Money amount() { return amount; }
    public PaymentNetwork network() { return network; }
    public PaymentMethod paymentMethod() { return paymentMethod; }
    public PanHash panHash() { return panHash; }
    public SystemTraceAuditNumber stan() { return stan; }
    public TransactionStatus status() { return status; }
    public AuthorizationCode authorizationCode() { return authorizationCode; }
    public AcquirerReferenceNumber arn() { return arn; }
    public String responseCode() { return responseCode; }
    public String cardLast4() { return cardLast4; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public List<DomainEvent<?>> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    // ── Functional "with" helpers (status change only — no events) ────────────

    public Transaction withStatus(TransactionStatus newStatus) {
        return withStatus(newStatus, Clock.systemUTC());
    }

    public Transaction withStatus(TransactionStatus newStatus, Clock clock) {
        return toBuilder().status(newStatus).updatedAt(Instant.now(clock)).build();
    }

    public Transaction withAuthCode(AuthorizationCode code) {
        return withAuthCode(code, Clock.systemUTC());
    }

    public Transaction withAuthCode(AuthorizationCode code, Clock clock) {
        return toBuilder().authorizationCode(code).updatedAt(Instant.now(clock)).build();
    }

    public Transaction withArn(AcquirerReferenceNumber acquirerReferenceNumber) {
        return withArn(acquirerReferenceNumber, Clock.systemUTC());
    }

    public Transaction withArn(AcquirerReferenceNumber acquirerReferenceNumber, Clock clock) {
        return toBuilder().arn(acquirerReferenceNumber).updatedAt(Instant.now(clock)).build();
    }

    public Transaction withResponseCode(String code) {
        return withResponseCode(code, Clock.systemUTC());
    }

    public Transaction withResponseCode(String code, Clock clock) {
        return toBuilder().responseCode(code).updatedAt(Instant.now(clock)).build();
    }

    // ── Domain aggregate methods — validate, change state, raise typed event ──

    /**
     * Creates a new transaction in INITIATED state and raises TransactionInitiatedEvent.
     * Use this factory in production code; the builder is for tests and adapters.
     */
    public static Transaction initiate(
            UUID id,
            MerchantId merchantId,
            TerminalId terminalId,
            Money amount,
            PaymentNetwork network,
            PaymentMethod paymentMethod,
            PanHash panHash,
            SystemTraceAuditNumber stan,
            Instant createdAt) {
        Transaction txn = builder()
            .id(id)
            .merchantId(merchantId)
            .terminalId(terminalId)
            .amount(amount)
            .network(network)
            .paymentMethod(paymentMethod)
            .panHash(panHash)
            .stan(stan)
            .status(TransactionStatus.INITIATED)
            .createdAt(createdAt)
            .build();
        txn.raiseEvent(DomainEvent.of(
            "transaction.initiated",
            id.toString(),
            "TRANSACTION",
            new TransactionInitiatedEvent(id, merchantId, amount, network, paymentMethod)
        ));
        return txn;
    }

    public Transaction authorize(AuthorizationCode authCode) {
        if (!status.canTransitionTo(TransactionStatus.AUTHORIZED)) {
            throw new InvalidStateTransitionException(id, status, TransactionStatus.AUTHORIZED);
        }
        Transaction updated = withStatus(TransactionStatus.AUTHORIZED).withAuthCode(authCode);
        updated.raiseEvent(DomainEvent.of(
            "transaction.authorized",
            id.toString(),
            "TRANSACTION",
            new TransactionAuthorizedEvent(id, amount, authCode, network)
        ));
        return updated;
    }

    public Transaction decline(String declineResponseCode) {
        if (!status.canTransitionTo(TransactionStatus.DECLINED)) {
            throw new InvalidStateTransitionException(id, status, TransactionStatus.DECLINED);
        }
        Transaction updated = withStatus(TransactionStatus.DECLINED).withResponseCode(declineResponseCode);
        updated.raiseEvent(DomainEvent.of(
            "transaction.declined",
            id.toString(),
            "TRANSACTION",
            new TransactionDeclinedEvent(id, declineResponseCode)
        ));
        return updated;
    }

    public Transaction initiateReversal() {
        if (!status.canTransitionTo(TransactionStatus.REVERSAL_PENDING)) {
            throw new InvalidStateTransitionException(id, status, TransactionStatus.REVERSAL_PENDING);
        }
        Transaction updated = withStatus(TransactionStatus.REVERSAL_PENDING);
        updated.raiseEvent(DomainEvent.of(
            "transaction.reversal_initiated",
            id.toString(),
            "TRANSACTION",
            new ReversalInitiatedEvent(id, amount)
        ));
        return updated;
    }

    public Transaction receiveChargeback() {
        if (!status.canTransitionTo(TransactionStatus.CHARGEBACK_RECEIVED)) {
            throw new InvalidStateTransitionException(id, status, TransactionStatus.CHARGEBACK_RECEIVED);
        }
        Transaction updated = withStatus(TransactionStatus.CHARGEBACK_RECEIVED);
        updated.raiseEvent(DomainEvent.of(
            "transaction.chargeback_received",
            id.toString(),
            "TRANSACTION",
            new ChargebackReceivedEvent(id, amount)
        ));
        return updated;
    }

    // ── Event lifecycle ───────────────────────────────────────────────────────

    public void raiseEvent(DomainEvent<?> event) {
        domainEvents.add(event);
    }

    public List<DomainEvent<?>> pullDomainEvents() {
        List<DomainEvent<?>> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return Collections.unmodifiableList(events);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private Builder toBuilder() {
        Builder b = new Builder();
        b.id = this.id;
        b.merchantId = this.merchantId;
        b.terminalId = this.terminalId;
        b.amount = this.amount;
        b.network = this.network;
        b.paymentMethod = this.paymentMethod;
        b.panHash = this.panHash;
        b.stan = this.stan;
        b.status = this.status;
        b.authorizationCode = this.authorizationCode;
        b.arn = this.arn;
        b.responseCode = this.responseCode;
        b.cardLast4 = this.cardLast4;
        b.createdAt = this.createdAt;
        b.updatedAt = this.updatedAt;
        b.domainEvents = new ArrayList<>(this.domainEvents);
        return b;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id;
        private MerchantId merchantId;
        private TerminalId terminalId;
        private Money amount;
        private PaymentNetwork network;
        private PaymentMethod paymentMethod;
        private PanHash panHash;
        private SystemTraceAuditNumber stan;
        private TransactionStatus status;
        private AuthorizationCode authorizationCode;
        private AcquirerReferenceNumber arn;
        private String responseCode;
        private String cardLast4;
        private Instant createdAt;
        private Instant updatedAt;
        private List<DomainEvent<?>> domainEvents = new ArrayList<>();

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder merchantId(MerchantId merchantId) { this.merchantId = merchantId; return this; }
        public Builder terminalId(TerminalId terminalId) { this.terminalId = terminalId; return this; }
        public Builder amount(Money amount) { this.amount = amount; return this; }
        public Builder network(PaymentNetwork network) { this.network = network; return this; }
        public Builder paymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; return this; }
        public Builder panHash(PanHash panHash) { this.panHash = panHash; return this; }
        public Builder stan(SystemTraceAuditNumber stan) { this.stan = stan; return this; }
        public Builder status(TransactionStatus status) { this.status = status; return this; }
        public Builder authorizationCode(AuthorizationCode authorizationCode) { this.authorizationCode = authorizationCode; return this; }
        public Builder arn(AcquirerReferenceNumber arn) { this.arn = arn; return this; }
        public Builder responseCode(String responseCode) { this.responseCode = responseCode; return this; }
        public Builder cardLast4(String cardLast4) { this.cardLast4 = cardLast4; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Transaction build() { return new Transaction(this); }
    }
}
