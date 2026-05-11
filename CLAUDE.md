# CLAUDE.md — Payments Platform Project
## Complete Project Requirements & Reference Document

> This is the single source of truth for the payments platform project.
> Every architectural decision, implementation detail, coding principle,
> testing strategy, and deployment requirement is captured here.
> Written for an AI coding assistant (Claude) to understand full context
> before writing any code.

---

## 1. Project Overview

### 1.1 What This Is

A production-grade distributed payments platform built in Java 21 / Spring Boot 3.x
that replicates the core internal architecture of an Indian payment switch and acquirer
processor — modelled on how companies like Payswiff, Juspay, and Pine Labs operate
internally. Every architectural decision mirrors real payments engineering.

This is NOT a Razorpay/Stripe integration wrapper. It IS the layer that companies
like Razorpay and Payswiff build internally.

### 1.2 Goals

**Primary:** Portfolio project demonstrating senior backend + full-stack engineering
competence for SDE2 roles at Indian product/fintech companies.

**Secondary:** Deep personal learning of payments domain from inside a payments company
to enable career transition from QA automation → backend software engineer.

**Target companies:** Zepto, Swiggy, Zomato (primary), Juspay, Razorpay, BharatPe
(fintech bonus targets).

**Target interview narrative:**
> "I built a payment switch from scratch — ISO 8583 over persistent TCP, SoftHSM2
> for ARQC verification and PIN block translation, dynamic QR with Redis TTL sessions,
> timeout and reversal handling with race condition detection, webhook dispatch with
> HMAC-SHA256 signing and exponential backoff retry, Spring Batch settlement with
> three-way reconciliation, and three React frontends — deployed on AWS ECS Fargate."

### 1.3 What This Is NOT

- Not a payments tutorial clone
- Not a Razorpay/Stripe wrapper
- Not connected to real banking rails (legally impossible for an individual)
- Not microservices for the sake of it — every service boundary is justified
- Not an AI/RAG project during the sprint — domain complexity IS the differentiator
  (ML fraud scoring is a planned post-June addition — see §24.3)

### 1.4 Real Money (Optional Layer — Post June)

Upstream connector is pluggable. After June, can swap one config property:
```yaml
upstream.provider: razorpay  # was: wiremock
```

Real UPI payments (₹1-₹10 test amounts) flow via Razorpay's licensed PA infrastructure.
Your switch layer, webhooks, and reconciliation handle real events. Legally clean —
you are a merchant using Razorpay, not a PA yourself.

Requires: sole proprietorship registration + Razorpay live account (~1 week setup).

### 1.5 Timeline

```
May 11 – June 30:   Project only, 36h/week
                    Full backend + all three frontends shipped

July onwards:       DSA + system design on weekdays
                    Project enhancements on weekends (12-18h/week)
                    AWS deployment, Razorpay live, load tests, Grafana
                    ML fraud scoring service (RAG/LangChain) — see §24.3
```

---

## 2. Architecture Principles

### 2.1 Hexagonal Architecture (Ports & Adapters) — Non-Negotiable

```
┌─────────────────────────────────────────────┐
│              Domain Core                     │
│  Pure Java 21. Zero external dependencies.  │
│  No Spring. No Kafka. No JPA. No HTTP.      │
│  Models, Ports (interfaces), Services.      │
└──────────────────┬──────────────────────────┘
                   │ depends on
┌──────────────────▼──────────────────────────┐
│           Application Layer                  │
│  Spring Boot wiring only. No business       │
│  logic. Beans, configs, use case            │
│  orchestration.                             │
└──────────────────┬──────────────────────────┘
                   │ depends on
┌──────────────────▼──────────────────────────┐
│            Adapters Layer                    │
│  All infrastructure. jPOS, SoftHSM2,        │
│  Kafka, HTTP, Postgres, Redis, S3.          │
│  Implements domain ports.                   │
│  Domain never imports from here.            │
└─────────────────────────────────────────────┘
```

**Enforcement:** domain/pom.xml has zero dependencies. ArchUnit tests enforce this
at build time. CI fails if domain imports Spring/Kafka/JPA.

### 2.2 SOLID — Applied in Code, Not Just README

**Single Responsibility**
One class, one job. No exceptions.
- `ProcessAuthorizationUseCase` — orchestrates only
- `FraudEngine` — fraud scoring only
- `TransactionStateMachine` — state transitions only
- `FeeWaterfallCalculator` — fee math only
- `TransactionMapper` — translation between domain and entity only

**Open/Closed**
New behaviour = new class. Existing code untouched.
```java
// Adding new network = add new class, zero changes to RoutingEngine
public interface NetworkRouter {
    boolean supports(PaymentNetwork network);
    NetworkConnection getConnection();
}
// Spring injects List<NetworkRouter> — RoutingEngine never changes
```

**Liskov Substitution**
Every adapter fully honours its port contract.
- Business failures (decline, timeout) encoded in result objects, never thrown
- WireMock adapter and Razorpay adapter are interchangeable from domain perspective
- Adding new result type causes compile error until all callers handle it (sealed interfaces)

**Interface Segregation**
Small focused interfaces. No forced unused methods.
```java
AuthorizationPort   // authorize + reverse only
RefundPort          // refund only
EMIPort             // EMI conversion only
BBPSPort            // bill fetch/pay only
HsmPort             // HSM operations only
```

**Dependency Inversion**
Domain depends on abstractions. Adapters depend on domain abstractions.
Constructor injection only — no @Autowired on fields. Ever.

### 2.3 Constructor Injection — Only Injection Style Allowed

```java
// ❌ NEVER — field injection
@Autowired
private AuthorizationPort authorizationPort;

// ✅ ALWAYS — constructor injection
public class PaymentSwitchService {
    private final AuthorizationPort authorizationPort;  // final

    public PaymentSwitchService(AuthorizationPort authorizationPort) {
        Objects.requireNonNull(authorizationPort);
        this.authorizationPort = authorizationPort;
    }
}
```

Domain classes have no Spring annotations. Plain Java. Testable with `new`.

### 2.4 Repository Pattern — Full Chain

```
Domain Port (interface, zero dependencies)
    ↓ implemented by
Adapter (PostgresTransactionRepository)
    ↓ uses
Mapper (TransactionMapper) — bidirectional translation
    ↓ uses
JPA Entity (TransactionEntity) — persistence model only
    ↓ backed by
Spring Data JPA Interface (JpaTransactionRepository)
```

Domain Transaction record never has @Entity.
JPA Entity never has domain logic.
Mapper is the only bridge between layers.
Database is swappable via config: `db.provider: postgres | mongo | csv`

### 2.5 Value Objects — No Primitive Obsession

```java
// ❌ Never
private String merchantId;
private double amount;

// ✅ Always
private MerchantId merchantId;    // self-validating, format-checked
private Money amount;              // BigDecimal + Currency, no double ever
private AuthorizationCode authCode; // validated format
private PanHash panHash;           // SHA-256 hash, never raw PAN
```

### 2.6 Immutability — Domain Objects Are Records

```java
public record Transaction(
    UUID id,
    MerchantId merchantId,
    Money amount,
    TransactionStatus status,
    Instant createdAt
) {
    // "Mutation" returns new object — original unchanged
    public Transaction withStatus(TransactionStatus newStatus) {
        return new Transaction(id, merchantId, amount, newStatus, createdAt);
    }
}
```

No setters. No null fields. Thread safe by design.

### 2.7 Sealed Result Types (Java 21)

```java
public sealed interface AuthorizationResult
    permits AuthorizationResult.Approved,
            AuthorizationResult.Declined,
            AuthorizationResult.Unknown,
            AuthorizationResult.Blocked {

    record Approved(String authCode, Instant authorizedAt) implements AuthorizationResult {}
    record Declined(String responseCode, String reason)    implements AuthorizationResult {}
    record Unknown(String reason, boolean reversalSent)    implements AuthorizationResult {}
    record Blocked(String fraudRule)                       implements AuthorizationResult {}
}

// Exhaustive pattern matching — compiler forces handling every case
Transaction updated = switch (result) {
    case AuthorizationResult.Approved a  -> stateMachine.transition(txn, AUTHORIZED);
    case AuthorizationResult.Declined d  -> stateMachine.transition(txn, DECLINED);
    case AuthorizationResult.Unknown u   -> stateMachine.transition(txn, UNKNOWN);
    case AuthorizationResult.Blocked b   -> stateMachine.transition(txn, DECLINED);
};
```

### 2.8 Domain Events — Decoupled Side Effects

```java
// Domain raises events, does not publish them
public Transaction authorize(AuthorizationCode authCode) {
    Transaction updated = this.withStatus(AUTHORIZED).withAuthCode(authCode);
    updated.domainEvents.add(new TransactionAuthorizedEvent(this.id, this.amount));
    return updated;
}

// Application layer saves then publishes
@Transactional
public AuthorizationResult processAuthorization(...) {
    Transaction authorized = transaction.authorize(authCode);
    transactionRepo.save(authorized);                          // save first
    authorized.pullDomainEvents().forEach(eventPublisher::publish);  // then publish
    return AuthorizationResult.approved(authorized);
}
```

### 2.9 Transactional Outbox Pattern

Kafka + DB consistency without distributed transactions:
```java
@Transactional
public void processTransaction(Transaction transaction) {
    transactionRepo.save(transaction);           // save transaction
    outboxRepo.save(new OutboxEntry(event));     // save event — SAME DB transaction
    // If either fails → both roll back → no ghost events, no lost events
}

@Scheduled(fixedDelay = 100)
public void publishOutboxEvents() {
    outboxRepo.findUnpublished().forEach(entry -> {
        kafkaProducer.send(entry.toKafkaRecord());
        outboxRepo.markPublished(entry.id());
    });
}
```

### 2.10 Command/Query Separation

```java
// Commands — change state, return minimal acknowledgement
public class ProcessAuthorizationUseCase {
    public AuthorizationResult execute(AuthorizationCommand command) { }
}

// Queries — return data, zero side effects, safe to call repeatedly
public class GetTransactionUseCase {
    public Optional<TransactionDetail> execute(UUID id) { }
}

public class SearchTransactionsUseCase {
    public Page<TransactionSummary> execute(TransactionSearchCriteria criteria) { }
}
```

### 2.11 Tell Don't Ask

```java
// ❌ Ask — breaks encapsulation
if (transaction.getStatus() == AUTHORIZED) {
    transaction.setStatus(CAPTURED);
}

// ✅ Tell — object owns its behaviour
transaction.capture();   // validates preconditions, sets state, raises event
```

### 2.12 ArchUnit — Architecture Enforced at Build Time

```java
@ArchTest
static final ArchRule domainHasNoDependencies =
    noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage(
            "..adapters..", "org.springframework..",
            "jakarta.persistence..", "org.apache.kafka.."
        );

@ArchTest
static final ArchRule noFieldInjection =
    noFields().should().beAnnotatedWith(Autowired.class);

@ArchTest
static final ArchRule controllersOnlyDependOnUseCases =
    classes().that().haveNameMatching(".*Controller")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(
            "..inbound.rest..", "..domain.port.inbound..",
            "..domain.model..", "java..", "org.springframework.web.."
        );
```

Architecture violations = CI build failure. Not a code review comment.

---

## 3. Maven Multi-Module Structure

```
payments-platform/
├── pom.xml                             ← parent, dependency management
├── CLAUDE.md
├── docker-compose.yml                  ← full local stack
├── docker-compose.aws.yml              ← AWS ECS overrides
├── .github/workflows/ci-cd.yml
├── .env.example                        ← template (gitignored .env)
│
├── domain/                             ← ZERO external dependencies
│   └── src/main/java/com/payments/domain/
│       ├── model/
│       │   ├── Transaction.java        ← record, immutable
│       │   ├── TransactionStatus.java  ← enum, state machine
│       │   ├── PaymentMethod.java      ← enum
│       │   ├── QRSession.java
│       │   ├── RoutingRule.java
│       │   ├── BinInfo.java
│       │   ├── MerchantProfile.java
│       │   ├── ChargebackRecord.java
│       │   ├── Money.java              ← value object
│       │   ├── MerchantId.java         ← value object
│       │   └── AuthorizationCode.java  ← value object
│       ├── port/
│       │   ├── inbound/
│       │   │   ├── ProcessPaymentUseCase.java
│       │   │   ├── ProcessRefundUseCase.java
│       │   │   ├── GenerateQRUseCase.java
│       │   │   ├── ProcessReversalUseCase.java
│       │   │   └── ReconcileUseCase.java
│       │   └── outbound/
│       │       ├── AuthorizationPort.java
│       │       ├── RefundPort.java
│       │       ├── WebhookDispatchPort.java
│       │       ├── SettlementPort.java
│       │       ├── FileStoragePort.java
│       │       ├── NotificationPort.java
│       │       ├── HsmPort.java
│       │       └── TransactionRepository.java
│       └── service/
│           ├── ProcessAuthorizationUseCase.java
│           ├── TransactionStateMachine.java
│           ├── RoutingEngine.java
│           ├── FraudEngine.java
│           ├── LuhnValidator.java
│           ├── QRSessionManager.java
│           └── FeeWaterfallCalculator.java
│
├── application/
│   └── src/main/java/com/payments/app/
│       ├── PaymentsApplication.java
│       └── config/
│           ├── AdapterConfig.java
│           ├── KafkaConfig.java
│           ├── RedisConfig.java
│           ├── SecurityConfig.java
│           └── ObservabilityConfig.java
│
├── adapters/
│   └── src/main/java/com/payments/adapters/
│       ├── inbound/
│       │   ├── iso8583/
│       │   │   ├── Iso8583ChannelAdapter.java
│       │   │   ├── MessageParser.java
│       │   │   └── MessageBuilder.java
│       │   ├── rest/
│       │   │   ├── QRController.java
│       │   │   ├── TransactionController.java
│       │   │   └── ReconciliationController.java
│       │   └── kafka/
│       │       └── TransactionEventConsumer.java
│       └── outbound/
│           ├── hsm/
│           │   ├── SoftHsmAdapter.java
│           │   └── MockHsmAdapter.java
│           ├── network/
│           │   ├── VisaNetAdapter.java
│           │   ├── NpciUpiRestAdapter.java
│           │   └── WireMockNetworkAdapter.java
│           ├── razorpay/
│           │   └── RazorpayUpstreamAdapter.java
│           ├── webhook/
│           │   └── HttpWebhookDispatchAdapter.java
│           ├── persistence/
│           │   ├── PostgresTransactionRepository.java
│           │   ├── PostgresAuditRepository.java
│           │   ├── RedisIdempotencyStore.java
│           │   ├── entity/
│           │   │   └── TransactionEntity.java
│           │   ├── jpa/
│           │   │   └── JpaTransactionRepository.java
│           │   └── mapper/
│           │       └── TransactionMapper.java
│           ├── storage/
│           │   ├── LocalFileStorageAdapter.java
│           │   └── S3FileStorageAdapter.java
│           └── notification/
│               └── JavaMailNotificationAdapter.java
│
├── services/
│   ├── acquiring-service/
│   ├── payment-switch/
│   ├── mock-upstream/
│   ├── webhook-dispatcher/
│   ├── merchant-simulator/
│   ├── settlement-service/
│   ├── reconciliation-service/
│   ├── notification-service/
│   └── chargeback-service/
│
├── frontend/
│   ├── simulator/                      ← Payment Simulator UI
│   ├── dashboard/                      ← Merchant Dashboard
│   └── ops/                            ← Ops Dashboard
│
└── tools/
    └── terminal-simulator/
```

---

## 4. Services — Specifications

### 4.1 terminal-simulator

Standalone Java app (not Spring Boot). Simulates physical POS terminal.

**Generates:**
- MTI 0100 — Authorization Request
- MTI 0200 — Financial/Capture Request
- MTI 0400 — Reversal Request
- MTI 0800 — Network Management (sign-on, echo)

**Scenarios (configurable):**
```
NORMAL_PURCHASE     → standard chip+PIN transaction
CONTACTLESS         → NFC, no PIN, amount < ₹5000
CONTACTLESS_PIN     → NFC, PIN required, amount > ₹5000
TIMEOUT             → no response simulation
DECLINE_NSF         → expects 51 response
DUPLICATE           → sends same STAN twice
PARTIAL_REVERSAL    → auth ₹10000, capture ₹8000
```

**Config:**
```yaml
terminal:
  id: TERM00042
  merchant-id: MERCH0000999
  switch-host: localhost
  switch-port: 8000
  timeout-ms: 15000
  scenario: NORMAL_PURCHASE
```

---

### 4.2 acquiring-service

Entry point for all terminal transactions.

**Ports:** 8000 (ISO 8583 TCP), 8080 (REST — QR, status)

**Inbound validation chain:**
1. ISO 8583 structural validation (bitmap, field formats, mandatory fields)
2. Luhn check on PAN
3. MAC verification (HsmPort)
4. Idempotency check (Redis — key: STAN:TerminalID:Date, TTL 24h)
5. Terminal active, not suspended, not stolen
6. Merchant active, KYC valid
7. Per-transaction limit check
8. Daily rolling volume check (Redis counter)

**On success:**
- Create transaction: status INITIATED
- Write audit log (append-only)
- Kafka: `transaction.initiated`
- Hand off to payment-switch

**Idempotency:**
```
Key:   {stan}:{terminalId}:{date}
Value: cached 0110 response bytes
TTL:   24 hours
Duplicate detected → return cached response, log detection, do NOT process
```

---

### 4.3 payment-switch

Core routing and processing engine.

**Flow:**
1. BIN lookup → Redis cache (L2) → Caffeine cache (L1) → Postgres (L3)
2. Routing decision (Visa / MC / RuPay / UPI)
3. HSM: ARQC verify, PIN block translate, ARPC generate
4. Fraud engine (inline, <50ms target)
5. Update state: AUTHORIZATION_PENDING
6. Store in correlation store (Redis, TTL 30s)
7. Send to upstream via connection pool
8. Receive response → match via ARN+STAN
9. Update state machine
10. Kafka event on every state transition
11. Audit log on every state transition

**Fraud engine rules:**
```
PAN velocity > 3 in 5 mins         → BLOCK
PAN velocity > 10 in 1 hour        → BLOCK
Terminal velocity > 200 in 1 hour  → FLAG HIGH
First transaction on PAN > ₹50000  → FLAG HIGH
Round amount (multiple of 10000)   → FLAG MEDIUM
Impossible travel (same PAN)       → BLOCK
High-risk MCC (5094,5944,7995)     → FLAG HIGH

Risk scores: LOW / MEDIUM / HIGH / BLOCK
BLOCK → decline locally, do not forward upstream
HIGH  → forward with fraud flag in Field 44
```

**ML fraud score hook (post-June, pluggable today):**
The `FraudScore` result record carries an `Optional<BigDecimal> mlRiskScore` field from
day one. During the sprint this is always `Optional.empty()`. Post-June, an async
`FraudScoringPort` outbound port receives the transaction and returns an ML score that
is merged into the final `FraudScore`. Rule-based engine always runs first and always
makes the real-time gate decision. ML score is a supplementary signal — it can tighten
thresholds (e.g. MEDIUM → BLOCK if mlRiskScore > 0.85) but never override a BLOCK.
See §24.3 for full spec.

**Timeout monitor:**
```
Scheduler: every 100ms
Threshold: 15 seconds
Action on timeout:
  1. Send MTI 0400 reversal
  2. State → REVERSAL_PENDING
  3. Kafka: transaction.reversal_initiated
  4. If reversal also times out → UNKNOWN
```

**Race condition (late response + reversal):**
```
0110 arrives AFTER reversal sent:
  - Check: is reversal confirmed?
  - Yes → discard 0110, stay REVERSED
  - No  → wait for 0410, then REVERSED
  - Never honour 0110 after reversal initiated
  - Log race condition, ops alert
```

---

### 4.4 mock-upstream

Three jPOS switch instances + one REST service:
- Mock Visa (port 8001) — ISO 8583 over TCP
- Mock Mastercard (port 8002) — ISO 8583 over TCP
- Mock NPCI NFS/RuPay (port 8003) — ISO 8583 over TCP
- Mock NPCI UPI (port 8004) — REST/JSON

**WireMock scenarios:**
```
APPROVE           → Field 39 = 00, auth code 483921
DECLINE_NSF       → Field 39 = 51
DECLINE_STOLEN    → Field 39 = 43
ISSUER_DOWN       → Field 39 = 91
TIMEOUT           → no response for 20 seconds
SLOW_RESPONSE     → response after 12 seconds
DROP_CONNECTION   → TCP connection dropped
DUPLICATE_RESP    → sends 0110 twice
LATE_RESPONSE     → sends 0110 after 16 seconds (race condition test)
```

Responds to MTI 0800 heartbeat with MTI 0810, Field 39 = 00.

---

### 4.5 webhook-dispatcher

Reliable event delivery to merchant endpoints.

**Kafka topics consumed (manual commit — never auto-commit):**
- `transaction.events`
- `settlement.events`
- `chargeback.events`

**Delivery flow:**
```
1. Consume event (do NOT commit offset yet)
2. Look up merchant webhook config
3. Build JSON payload
4. Sign: HMAC-SHA256(secret, payload_bytes) → hex
5. Header: X-Payswiff-Signature: sha256={hex}
6. Header: X-Payswiff-Event: payment.authorized
7. Header: X-Payswiff-Delivery: {uuid}
8. POST to merchant URL (timeout: 5s)
9. 2xx → commit offset, record DELIVERED
10. Non-2xx or timeout:
    Retry 1: 30 seconds
    Retry 2: 2 minutes
    Retry 3: 10 minutes
    After 3 fails → dead.letter topic, record FAILED
11. Commit offset after final outcome
```

**Event types emitted:**
```
payment.initiated, payment.authorized, payment.captured,
payment.declined, payment.reversal_initiated, payment.reversed,
payment.unknown, payment.refund_initiated, payment.refunded,
settlement.processed, settlement.payout_initiated,
chargeback.received, chargeback.evidence_required,
chargeback.won, chargeback.lost
```

---

### 4.6 merchant-simulator

Simulates a merchant's backend. Receives webhooks, validates HMAC, can trigger refunds.

**Config:**
```yaml
merchant:
  id: MERCH0000999
  webhook-secret: test-webhook-secret-key
  webhook-url: http://merchant-simulator:9000/webhooks
  simulate-failure: false
  failure-rate: 0.3   # 30% return 500 for retry testing
```

---

### 4.7 settlement-service

Spring Batch job. Runs daily at 23:30 IST.

**Steps:**
```
Step 1: TransactionAggregationStep
  Reader:    JpaPagingItemReader (CAPTURED transactions, page 1000)
  Processor: validate, format, calculate fees
  Writer:    group by network

Step 2: FileGenerationStep
  Generate CSV per network → S3
  VISA_SETTLEMENT_YYYYMMDD_BATCH001.csv
  MC_SETTLEMENT_YYYYMMDD_BATCH001.csv
  NPCI_SETTLEMENT_YYYYMMDD_BATCH001.csv

Step 3: BatchValidationStep
  Verify totals balance
  No duplicates
  All merchants active
  Sign file (SoftHSM2 digital signature)

Step 4: NetworkSubmissionStep
  POST to mock network endpoints
  Record network batch IDs
  Update transactions: CAPTURED → SETTLEMENT_PENDING

Step 5: NotificationStep
  Kafka: settlement.submitted
  Email ops: job completion summary
```

**Error handling:** Job failure → immediate CRITICAL email → ops must resolve before
settlement window closes. Spring Batch restart from last checkpoint.

Manual trigger: `POST /settlement/trigger`

---

### 4.8 reconciliation-service

Three-way reconciliation: switch log vs network file vs bank statement.

**Inputs:**
- Source 1: Postgres (SETTLEMENT_PENDING transactions)
- Source 2: S3 (network settlement confirmation CSV)
- Source 3: S3 (mock bank MT940 statement)

**Matching algorithm:**
```
For each transaction in Postgres:
  Match by ARN → network file
  Match by amount+date → bank statement
  All three match → RECONCILED
  Any mismatch → categorize → exception queue
```

**Mismatch categories:**
```
MISSING_IN_NETWORK    in switch, not in network file
MISSING_IN_SWITCH     in network, not in switch (CRITICAL)
MISSING_IN_BANK       in both, not in bank statement
AMOUNT_MISMATCH       same transaction, different amounts
DUPLICATE_IN_NETWORK  network file has same ARN twice
UNKNOWN_RESOLVED      UNKNOWN transaction found in network file
```

**UNKNOWN resolution:**
- Found in network file → AUTHORIZED (issuer processed it)
- Not in network file → FAILED (never processed)

**Fee waterfall (post-reconciliation):**
```
Gross amount
  - Interchange fee → issuing bank
  - Network assessment → Visa/MC/NPCI
  - Payswiff MDR → revenue
  = Net to merchant
  - Reserve withholding (5%)
  = Payout amount → NEFT to merchant bank
```

**Outputs to S3:**
- `RECON_YYYYMMDD_SUMMARY.json`
- `RECON_YYYYMMDD_EXCEPTIONS.csv`

Manual trigger: `POST /reconciliation/trigger`

---

### 4.9 notification-service

Kafka consumer → email dispatcher. No business logic.

**Kafka topics consumed:**
```
health.events, settlement.events, chargeback.events,
dead.letter, transaction.events (high-value only)
```

**Email templates (Thymeleaf):**
```
ops/job-failed.html              ← CRITICAL, sent immediately
ops/job-completed.html
ops/reconciliation-alert.html
ops/dead-letter-alert.html       ← CRITICAL
ops/switch-disconnected.html
ops/daily-digest.html            ← 8 AM

merchant/settlement-processed.html
merchant/payout-initiated.html
merchant/chargeback-received.html
merchant/chargeback-outcome.html
```

**Job failed email includes:**
- Batch ID, job name, failed step
- Error message + exception type
- Records processed before failure / records remaining
- Stack trace (attached)
- CloudWatch logs link
- Escalation deadline

**Mail config:**
```yaml
# Local (MailHog)
spring.mail.host: localhost
spring.mail.port: 1025

# Production (SendGrid)
spring.mail.host: smtp.sendgrid.net
spring.mail.port: 587
spring.mail.password: ${SENDGRID_API_KEY}
```

---

### 4.10 chargeback-service

Full chargeback lifecycle management.

**States:**
```
RECEIVED → ACCEPTED
         → CONTESTED → EVIDENCE_SUBMITTED → WON
                     → EVIDENCE_SUBMITTED → LOST
                     → WITHDRAWN
```

**On receipt:**
1. Parse chargeback from mock network
2. Create chargeback record
3. Find original transaction
4. Debit merchant reserve: amount + ₹350 fee
5. Park in suspense account
6. Set response deadline (Visa: 30 days, MC: 45 days)
7. Kafka: chargeback.received
8. Email merchant + ops

**Evidence package (ZIP → S3):**
```
CB_EVIDENCE_ARN_{arn}.zip:
  - Original ISO 8583 0100 bytes
  - ARQC verification result (VERIFIED_OK)
  - PIN verification result
  - EMV chip read confirmation (Field 22)
  - ARPC confirmation
  - Terminal audit log
  - Authorization code (Field 38)
```

**Reserve accounts:**
```sql
merchant_reserve_accounts: merchant_id, balance, currency, updated_at
reserve_transactions: id, merchant_id, transaction_id, chargeback_id,
                      type, amount, balance_after, created_at
-- type: WITHHOLD | RELEASE | CHARGEBACK_DEBIT | CHARGEBACK_CREDIT
```

---

## 5. Payment Flows

### 5.1 Card Transaction — Contact EMV

```
Terminal: card inserted → EMV reads chip → DDA/CDA offline auth
        → ARQC generated → PIN entered → PIN block encrypted
        → ISO 8583 MTI 0100 built → sent over TCP

Acquiring: structural validation → Luhn check → MAC verify (HSM)
         → idempotency check → terminal/merchant validation
         → limits check → create INITIATED → audit log → Kafka

Switch: BIN lookup → routing decision → ARQC verify (HSM)
      → PIN block translate (HSM) → ARPC generate (HSM)
      → fraud score → state: AUTHORIZATION_PENDING
      → correlation store → send 0100 to mock network

Mock Network: validates → routes to mock issuer → approve/decline
            → sends 0110 back

Switch: match response via ARN+STAN → verify ARPC
      → Field 39=00 → state: AUTHORIZED → Kafka: transaction.authorized

Acquiring: build 0110 response → send to terminal
Terminal: print receipt
Webhook: payment.authorized → merchant-simulator
```

### 5.2 Contactless NFC

Same as 5.1 with:
- Shorter EMV flow (fewer card APDU round trips)
- Amount ≤ ₹5000: No CVM (no PIN), Field 22 = 07
- Amount > ₹5000: PIN required even for tap

### 5.3 Timeout + Reversal

```
Terminal → switch sends 0100 → no response for 15 seconds
Timeout monitor fires:
  → build MTI 0400 with Field 90 (original data elements)
  → send reversal → state: REVERSAL_PENDING
  → Kafka: transaction.reversal_initiated

Mock network responds 0410:
  Field 39 = 00: reversal accepted → state: REVERSED
  Field 39 = 25: original not found → state: REVERSED (already OK)

Race condition (late 0110 + reversal):
  0110 arrives after reversal sent:
  → check reversal status
  → discard 0110 regardless
  → wait for 0410 → REVERSED
  → log race condition event + ops alert
```

### 5.4 Dynamic QR Payment

```
Cashier: POST /qr/generate { merchantId, amount, currency, orderId }

Acquiring:
  → generate txnRef: TXN{yyyyMMddHHmmss}{merchantId}{seq}
  → Redis session: { merchantId, amount, status:PENDING, expiresAt:+5min }
  → UPI string: upi://pay?pa=merchant@payswiff&am=6000.00&tr={txnRef}&cu=INR
  → ZXing QR image (error correction M, 300x300)
  → return { txnRef, qrImageBase64, expiresAt }

POS: displays QR
Customer: scans → amount pre-filled → approves UPI PIN

NPCI → Payswiff credit notification:
  POST /upi/credit { npciTxnId, payerVPA, payeeVPA, amount, txnRef }

Acquiring:
  → lookup Redis session by txnRef
  → amount matches → COMPLETED
  → create transaction: AUTHORIZED
  → delete QR session
  → Kafka: transaction.authorized

POS: polls GET /qr/status/{txnRef} (every 2s)
  OR: SSE event pushed immediately
Response: { status:COMPLETED, npciTxnId, paidAt }

TTL expiry:
  → Redis key auto-expires
  → late credit arrives → session not found → exception queue
  → auto-refund initiated → ops alert
```

### 5.5 Static QR Payment

```
Setup: Payswiff assigns VPA → generates static QR → prints at merchant counter
  QR: upi://pay?pa=merchant_999@payswiff&pn=MerchantName&mc=5411

Customer: scans → enters amount manually → pays

NPCI credit notification (no txnRef):
  → match by payeeVPA + amount + timestamp window
  → record transaction: AUTHORIZED
  → webhook: payment.authorized

Limitation: no transaction reference = harder reconciliation
            If two ₹6000 payments arrive in same minute → ambiguous
            This is documented — dynamic QR preferred
```

### 5.6 UPI Collect

```
POST /upi/collect { payerVPA, amount, expiry:180 }
→ Acquiring → Mock NPCI: send collect request
→ Mock NPCI: notification to customer UPI app
→ Customer: APPROVE → UPI PIN → NPCI credits
→ Acquiring receives outcome webhook from Mock NPCI
→ State updated → Kafka event → merchant webhook
```

### 5.7 Refund (Post-Settlement)

```
Precondition: PAID_OUT state

POST /transactions/{txnId}/refund { amount, reason }
→ Validate txnId is PAID_OUT
→ Validate refund amount ≤ original
→ Create refund: REFUND_INITIATED
→ Kafka: payment.refund_initiated
→ Build MTI 0200 processing code 200000 OR Razorpay refund API
→ REFUNDED (3-7 business days to cardholder)

REVERSAL vs REFUND:
  Reversal: pre-settlement, cancels auth hold, instant, MTI 0400
  Refund:   post-settlement, new transaction, 3-7 days, MTI 0200
  System routes correctly based on transaction state
```

### 5.8 Partial Reversal

```
Auth approved: ₹10,000 (AUTHORIZED)
Capture: ₹8,000 (CAPTURED)
Switch sends:
  → MTI 0200 for ₹8,000 (capture)
  → MTI 0400 for ₹2,000 (partial reversal, Field 95: replacement amounts)
Issuer: hold ₹10,000 → capture ₹8,000 → release ₹2,000
Net cardholder charge: ₹8,000
```

---

## 6. ISO 8583 Specification

### 6.1 Library

**jPOS 2.1.9** — production-grade, Apache 2.0, used by real payment switches globally.

```xml
<dependency>
    <groupId>org.jpos</groupId>
    <artifactId>jpos</artifactId>
    <version>2.1.9</version>
</dependency>
```

### 6.2 Message Type Indicators (MTI)

```
0100  Authorization Request          terminal → switch
0110  Authorization Response         switch → terminal
0120  Authorization Advice           offline approval advice
0200  Financial Transaction Request  capture
0210  Financial Transaction Response
0400  Reversal Request
0410  Reversal Response
0420  Reversal Advice
0800  Network Management Request     echo, sign-on, key exchange
0810  Network Management Response
```

### 6.3 Key Fields

```
Field 2:   PAN — LLVAR, max 19 digits, never stored in plaintext
Field 3:   Processing Code — 000000=purchase, 200000=refund, 010000=cash
Field 4:   Amount — 12 digits, implied 2 decimal places (000000600000 = ₹6000)
Field 7:   Transmission DateTime — MMDDHHmmss
Field 11:  STAN — 6 digits, sequential per terminal
Field 12:  Local Transaction Time — HHmmss
Field 13:  Local Transaction Date — MMDD
Field 14:  Card Expiry — YYMM
Field 22:  POS Entry Mode — 07=chip, 91=contactless EMV, 80=magstripe, 90=fallback
Field 25:  POS Condition Code
Field 33:  Forwarding Institution ID (Payswiff acquirer BIN)
Field 35:  Track 2 Equivalent — LLVAR, encrypted
Field 37:  Retrieval Reference Number (RRN) — 12 alphanumeric
Field 38:  Authorization Code — 6 alphanumeric (in response if approved)
Field 39:  Response Code — 2 alphanumeric
Field 41:  Terminal ID — 8 alphanumeric
Field 42:  Merchant ID — 15 alphanumeric
Field 44:  Additional Response Data (fraud flag)
Field 49:  Currency Code — 356=INR
Field 52:  PIN Data — 8 bytes binary (encrypted PIN block, ISO 9564 Format 0)
Field 55:  ICC/EMV Data — LLLVAR binary (ARQC, ATC, TVR, TSI, AIP)
Field 64:  Message Authentication Code — 8 bytes binary
Field 90:  Original Data Elements — in reversals
Field 95:  Replacement Amounts — partial reversal
Field 128: Secondary MAC
```

### 6.4 Network Management Processing Codes

```
000000  Sign-On
000200  Sign-Off
301000  Echo (heartbeat)
901000  Key Exchange Request
901010  Key Exchange Response
```

### 6.5 Bitmap

Primary (8 bytes = 64 bits): fields 1-64
Secondary (8 bytes = 64 bits): fields 65-128
Bit N set = Field N present

### 6.6 Variable Length Fields

```
LLVAR:  2-digit length prefix + data (max 99 bytes)
LLLVAR: 3-digit length prefix + data (max 999 bytes)
```

### 6.7 Key Response Codes and Switch Behaviour

```
00  APPROVED      → AUTHORIZED state
51  NSF           → DECLINED (insufficient funds)
05  DO_NOT_HONOR  → DECLINED (do not retry)
91  ISSUER_DOWN   → retry once after 2s, then DECLINED
92  CANT_ROUTE    → retry alternate route, then DECLINED
04  PICK_UP       → DECLINED + card retention alert
41  LOST_CARD     → DECLINED + card retention alert
43  STOLEN_CARD   → DECLINED + card retention alert + ops alert
38  PIN_EXCEEDED  → DECLINED + log PIN lockout
55  WRONG_PIN     → DECLINED + increment PIN attempt counter
30  FORMAT_ERROR  → log message error + ops alert
```

### 6.8 Luhn Validation

Every PAN validated before processing:
```
Step 1: Double every second digit from right
Step 2: If doubled > 9, subtract 9
Step 3: Sum all digits
Step 4: Sum divisible by 10 → valid
Invalid PAN → reject before HSM, save round trip
```

### 6.9 Dual-Message vs Single-Message

```
Single message (Visa debit, domestic):
  One 0100 does auth + capture
  Goes into settlement automatically
  No separate capture needed

Dual message (Visa credit, Mastercard):
  0100 = authorization (hold only)
  0200 = capture (triggers settlement)
  Allows amount adjustment between auth and capture
  Hotels, airlines, e-commerce use this
```

---

## 7. HSM Specification

### 7.1 Technology: SoftHSM2

SoftHSM2 is a PKCS#11-compliant software HSM for local development.
Production would use Thales Luna or Safenet hardware HSM.

```bash
apt-get install softhsm2
softhsm2-util --init-token --slot 0 --label "payments-hsm" \
              --pin 1234 --so-pin 5678
```

**Java integration via SunPKCS11:**
```java
String config = "name=SoftHSM\nlibrary=/usr/lib/softhsm/libsofthsm2.so\n";
Provider provider = Security.getProvider("SunPKCS11").configure(config);
Security.addProvider(provider);
```

### 7.2 Key Hierarchy

```
Zone Master Key (ZMK)          ← root of trust, set physically
    ↓ protects
Zone PIN Key (ZPK)             ← PIN encryption in transit
Message Authentication Key (MAK) ← ISO 8583 MAC generation
Data Encryption Key (DEK)      ← sensitive field encryption

Issuer Master Key (IMK)        ← per issuer BIN range
    ↓ diversified by PAN + ATC
Card Session Key               ← unique per transaction
    ↓ used for
ARQC Verification + ARPC Generation
```

### 7.3 HSM Operations

**1. MAC Verification (every inbound message)**
```
Input:  message fields + Field 64 + MAK key handle
Output: VALID or INVALID
Failure → reject immediately, ops alert
```

**2. PIN Block Translation**
```
Input:  PIN block (under terminal ZPK) + terminal key + network key
Output: PIN block (under network ZPK)
PIN NEVER in plaintext outside HSM boundary — ever
```

**3. ARQC Verification**
```
Input:  ARQC + PAN + ATC + transaction data + IMK
Process: derive card session key = f(IMK, PAN, ATC)
         recompute expected ARQC from transaction data
         compare
Output: VERIFIED or FAILED
FAILED → decline locally, do not forward
```

**4. ARPC Generation**
```
Input:  ARQC + authorization response code + IMK
Output: ARPC bytes → goes in response Field 55
Card verifies ARPC — proves response is genuinely from issuer
```

**5. Key Exchange (daily, on session start)**
```
Load ZMK from secure storage
Derive session keys (ZPK, MAK, DEK) under ZMK
Store in HSM for session
Rotate daily via MTI 0800 key exchange protocol
```

### 7.4 MockHsmAdapter (Fallback)

When SoftHSM2 setup blocks progress:
```yaml
hsm.provider: mock   # returns hardcoded VERIFIED results
```
Same HsmPort interface. Swap back to softhsm when ready.
Do NOT use mock in staging or prod.

---

## 8. Switch-to-Switch Communication

### 8.1 Transport

**Raw TCP with persistent connections** — not HTTP.
- ISO 8583 is binary protocol, designed for TCP
- Persistent connection avoids handshake overhead per transaction
- At target TPS, new connection per message is impossible
- Both sides can send independently on same connection

**Connection topology:**
```
Payswiff switch maintains:
  Visa VisaNet:    primary + secondary (different PoP)
  MC Banknet:      primary + secondary
  NPCI NFS:        primary + secondary
  NPCI UPI:        primary + secondary (REST over leased line)
```

**Leased lines / MPLS** — not public internet. Required by network security standards.

### 8.2 Network Management (Always Running)

**Heartbeat every 30 seconds:**
```
Payswiff → Network: MTI 0800, Field 3 = 301000 (Echo)
Network → Payswiff: MTI 0810, Field 39 = 00
No response → connection dead → reconnect with exponential backoff
```

**Daily sign-on:**
```
MTI 0800, Field 3 = 000000 (Sign-On)
MTI 0810, Field 39 = 00
```

**Key exchange:**
```
MTI 0800, Field 3 = 901000 (Key Exchange Request)
Contains new encrypted session keys
MTI 0810, Field 3 = 901010 (Key Exchange Response)
```

### 8.3 Message Correlation

Each switch adds its own identifiers:
```
Terminal:         STAN (Field 11), Terminal ID (Field 41)
Payswiff switch:  ARN (Acquirer Reference Number, Field 37)
                  Forwarding Institution ID (Field 33)
Visa/MC:          Network Transaction ID
Issuer:           Issuer Reference Number, Auth Code (Field 38)
```

All stored and correlated in transaction record.
ARN is the universal reference for chargebacks and disputes.

### 8.4 Correlation Store (Redis)

```
Key:   correlation:{arn}:{stan}
Value: { originalRequest, sentAt, status:PENDING }
TTL:   30 seconds (2× timeout window)

On response: retrieve by ARN+STAN, remove, process
On timeout:  scan for entries older than 15s → initiate reversal
```

### 8.5 Connection Manager

```java
public interface SwitchConnection {
    void connect();
    CompletableFuture<Iso8583Message> send(Iso8583Message request, Duration timeout);
    void sendHeartbeat();
    boolean isAlive();
    void reconnect();
}

// Connection pool per network: primary + secondary
// Health monitored by heartbeat
// Reconnect: exponential backoff 1s → 2s → 4s → 8s → 30s max
// Failover: primary down → route via secondary automatically
```

---

## 9. Transaction State Machine

### 9.1 All States

```java
public enum TransactionStatus {
    INITIATED,
    AUTHORIZATION_PENDING,
    AUTHORIZED,
    DECLINED,
    CAPTURED,
    REVERSAL_PENDING,
    REVERSED,
    UNKNOWN,                      // timeout — unresolved
    REFUND_INITIATED,
    REFUND_PENDING,
    REFUNDED,
    REFUND_FAILED,
    SETTLEMENT_PENDING,
    RECONCILED,
    PAID_OUT,
    CHARGEBACK_RECEIVED,
    CHARGEBACK_CONTESTED,
    CHARGEBACK_EVIDENCE_SUBMITTED,
    CHARGEBACK_WON,
    CHARGEBACK_LOST
}
```

### 9.2 Valid Transitions

```java
Map.ofEntries(
  entry(INITIATED,             Set.of(AUTHORIZATION_PENDING, DECLINED)),
  entry(AUTHORIZATION_PENDING, Set.of(AUTHORIZED, DECLINED, REVERSAL_PENDING, UNKNOWN)),
  entry(AUTHORIZED,            Set.of(CAPTURED, REVERSAL_PENDING, REFUND_INITIATED)),
  entry(CAPTURED,              Set.of(SETTLEMENT_PENDING, REFUND_INITIATED)),
  entry(REVERSAL_PENDING,      Set.of(REVERSED, UNKNOWN)),
  entry(UNKNOWN,               Set.of(AUTHORIZED, REVERSED, DECLINED)),
  entry(REFUND_INITIATED,      Set.of(REFUND_PENDING, REFUND_FAILED)),
  entry(REFUND_PENDING,        Set.of(REFUNDED, REFUND_FAILED)),
  entry(SETTLEMENT_PENDING,    Set.of(RECONCILED)),
  entry(RECONCILED,            Set.of(PAID_OUT, CHARGEBACK_RECEIVED)),
  entry(PAID_OUT,              Set.of(CHARGEBACK_RECEIVED, REFUND_INITIATED)),
  entry(CHARGEBACK_RECEIVED,   Set.of(CHARGEBACK_CONTESTED, CHARGEBACK_LOST)),
  entry(CHARGEBACK_CONTESTED,  Set.of(CHARGEBACK_EVIDENCE_SUBMITTED)),
  entry(CHARGEBACK_EVIDENCE_SUBMITTED, Set.of(CHARGEBACK_WON, CHARGEBACK_LOST))
)
```

Invalid transition → `InvalidStateTransitionException` → 500 → ops alert.
Every transition logged to `transaction_events` + `audit_log`.

---

## 10. Database Design

### 10.1 Technology Choice

```
PostgreSQL:   transactional data — transactions, merchants, chargebacks
              ACID, optimistic locking, SERIALIZABLE isolation
              NEVER float/double for money — always NUMERIC(15,2)

MongoDB:      audit logs, event store, raw ISO 8583 message archive
              Flexible schema, append-only, document model fits naturally

CSV/S3:       settlement files, reconciliation reports, bank statements
              File-based flows — not a database
```

### 10.2 Repository Port → Adapter Chain

```
domain/TransactionRepository.java          ← interface, zero DB imports
adapters/PostgresTransactionRepository.java ← implements port
adapters/entity/TransactionEntity.java     ← JPA entity, never leaks to domain
adapters/jpa/JpaTransactionRepository.java ← Spring Data, JPA mechanics only
adapters/mapper/TransactionMapper.java     ← bidirectional translation

Swap: db.provider: postgres | mongo | csv → zero domain changes
```

### 10.3 Core Schema

```sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stan VARCHAR(6) NOT NULL,
    rrn VARCHAR(12),
    arn VARCHAR(23),
    terminal_id VARCHAR(8) NOT NULL,
    merchant_id VARCHAR(15) NOT NULL,
    pan_hash VARCHAR(64) NOT NULL,         -- SHA-256, NEVER store PAN
    card_last4 CHAR(4),
    network VARCHAR(20),
    payment_method VARCHAR(30) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,         -- NEVER float/double
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL,
    authorization_code VARCHAR(6),
    response_code CHAR(2),
    risk_score VARCHAR(10),
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    npci_txn_id VARCHAR(50),
    qr_txn_ref VARCHAR(50),
    version BIGINT NOT NULL DEFAULT 0,     -- optimistic locking
    upstream_request_at TIMESTAMPTZ,
    upstream_response_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    settled_at TIMESTAMPTZ,
    reconciled_at TIMESTAMPTZ,
    paid_out_at TIMESTAMPTZ
);

-- Indexes — every one has a reason
CREATE UNIQUE INDEX idx_transactions_idempotency
    ON transactions(idempotency_key);
    -- Used by: idempotency check on every transaction

CREATE INDEX idx_transactions_merchant_status_date
    ON transactions(merchant_id, status, created_at DESC);
    -- Used by: dashboard search

CREATE INDEX idx_transactions_settlement_pending
    ON transactions(status, created_at)
    WHERE status IN ('CAPTURED', 'SETTLEMENT_PENDING');
    -- Partial index: reconciliation query

CREATE INDEX idx_transactions_arn
    ON transactions(arn)
    WHERE arn IS NOT NULL;
    -- Partial index: chargeback + network matching

CREATE INDEX idx_transactions_qr_ref
    ON transactions(qr_txn_ref)
    WHERE qr_txn_ref IS NOT NULL;
    -- Partial index: QR session matching

-- Audit log — append only, no UPDATE/DELETE privileges
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    actor_service VARCHAR(100) NOT NULL,
    transaction_id UUID,
    aggregate_id VARCHAR(100),
    aggregate_type VARCHAR(50),
    previous_state VARCHAR(50),
    new_state VARCHAR(50),
    event_data JSONB NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
REVOKE UPDATE, DELETE ON audit_log FROM payments_app_user;

CREATE TABLE transaction_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    actor_service VARCHAR(50) NOT NULL,
    event_data JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE merchants (
    id VARCHAR(15) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    mcc CHAR(4) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    webhook_url VARCHAR(500),
    webhook_secret VARCHAR(100),
    mdr_percentage NUMERIC(5,4) NOT NULL DEFAULT 0.0150,
    per_txn_limit NUMERIC(15,2) NOT NULL DEFAULT 500000.00,
    daily_limit NUMERIC(15,2) NOT NULL DEFAULT 5000000.00,
    reserve_percentage NUMERIC(5,4) NOT NULL DEFAULT 0.0500,
    vpa VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE terminals (
    id VARCHAR(8) PRIMARY KEY,
    merchant_id VARCHAR(15) NOT NULL REFERENCES merchants(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    terminal_key_id VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE bin_table (
    bin_prefix VARCHAR(8) PRIMARY KEY,
    network VARCHAR(20) NOT NULL,
    issuer_name VARCHAR(100),
    card_type VARCHAR(20),
    card_product VARCHAR(50),
    country_code CHAR(2) DEFAULT 'IN',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) NOT NULL,
    merchant_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    response_code INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE chargebacks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    arn VARCHAR(23),
    network VARCHAR(20) NOT NULL,
    reason_code VARCHAR(10) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    chargeback_fee NUMERIC(10,2) NOT NULL DEFAULT 350.00,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    response_deadline TIMESTAMPTZ NOT NULL,
    evidence_s3_key VARCHAR(500),
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE TABLE settlement_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    network VARCHAR(20) NOT NULL,
    batch_date DATE NOT NULL,
    batch_file_s3_key VARCHAR(500),
    transaction_count INT NOT NULL,
    gross_amount NUMERIC(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATED',
    submitted_at TIMESTAMPTZ,
    network_batch_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE reconciliation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_date DATE NOT NULL UNIQUE,
    total_transactions INT,
    matched_count INT,
    mismatch_count INT,
    unknown_resolved_count INT,
    summary_s3_key VARCHAR(500),
    exceptions_s3_key VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE merchant_reserve_accounts (
    merchant_id VARCHAR(50) PRIMARY KEY,
    balance NUMERIC(15,2) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE reserve_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id VARCHAR(50) NOT NULL,
    transaction_id UUID,
    chargeback_id UUID,
    type VARCHAR(30) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    balance_after NUMERIC(15,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at)
    WHERE published = FALSE;
```

### 10.4 Flyway Migrations (Non-Negotiable)

```
db/migration/
  V1__create_transactions.sql
  V2__create_merchants_terminals.sql
  V3__create_audit_log.sql
  V4__create_webhook_deliveries.sql
  V5__create_chargebacks.sql
  V6__create_settlement_batches.sql
  V7__create_reconciliation.sql
  V8__create_reserve_accounts.sql
  V9__create_outbox.sql
```

Never modify existing migrations. Add new ones only.
Flyway runs on startup. DB always in sync with code.

### 10.5 N+1 Prevention

```java
// 1. JOIN FETCH for eager loads
@Query("SELECT t, m FROM TransactionEntity t JOIN FETCH t.merchant m WHERE ...")
List<TransactionEntity> findWithMerchant(...);

// 2. @BatchSize for collections
@OneToMany(fetch = FetchType.LAZY)
@BatchSize(size = 25)   // one IN query instead of N queries
private List<TransactionEventEntity> events;

// 3. Projections — only fetch needed fields
public interface TransactionSummary {
    UUID getId(); BigDecimal getAmount(); TransactionStatus getStatus();
}
List<TransactionSummary> findByMerchantId(String merchantId);
// SELECT id, amount, status FROM transactions WHERE merchant_id = ?

// 4. Specifications for dynamic queries — one query regardless of filters
Specification<TransactionEntity> spec = buildSpec(criteria);
return jpa.findAll(spec, pageable).map(mapper::toSummary);
```

### 10.6 Optimistic Locking

```java
@Entity
public class TransactionEntity {
    @Version
    private Long version;   // Postgres enforces — two instances can't corrupt same row
}

// On conflict: OptimisticLockException → retry once → then fail
// No database locks held → high throughput
```

### 10.7 Connection Pool Sizing (HikariCP)

```yaml
# acquiring-service — high throughput, short queries
hikari.maximum-pool-size: 20
hikari.minimum-idle: 5
hikari.connection-timeout: 2000

# settlement-service — batch, low concurrency, long queries
hikari.maximum-pool-size: 5
hikari.minimum-idle: 1
hikari.connection-timeout: 5000
```

Total connections across all services: ~60. RDS t3.micro max: 87. Safe.

### 10.8 Read/Write Separation

```java
@Transactional(readOnly = true)    // routes to read replica
public Page<TransactionSummary> searchTransactions(...) { }

@Transactional                     // routes to primary
public Transaction save(...) { }
```

Separate CQRS-lite interfaces:
- `TransactionRepository` — write side (save, updateStatus)
- `TransactionQueryRepository` — read side (search, summaries, dashboard stats)

### 10.9 BigDecimal Rules — Absolute

```java
// ALWAYS
new BigDecimal("6000.00")
amount.setScale(2, RoundingMode.HALF_UP)
amount.divide(divisor, 2, RoundingMode.HALF_UP)

// NEVER
double amount = 6000.00;           // floating point
new BigDecimal(6000.00);           // double constructor — imprecise
amount.divide(divisor);            // no scale → ArithmeticException
```

---

## 11. Redis Key Patterns

```
idempotency:{stan}:{terminalId}:{date}          TTL: 24h
bin:cache:{binPrefix}                            TTL: 24h
merchant:config:{merchantId}                     TTL: 5min (invalidated on update)
terminal:config:{terminalId}                     TTL: 5min
routing:rules                                    TTL: 1h
qr:session:{txnRef}                              TTL: 5min (configurable)
correlation:{arn}:{stan}                         TTL: 30s
fraud:velocity:pan:{panHash}:5min                TTL: 5min (sliding)
fraud:velocity:pan:{panHash}:1hour               TTL: 1h (sliding)
fraud:velocity:terminal:{terminalId}:1hour       TTL: 1h
merchant:daily:volume:{merchantId}:{date}        TTL: end of business day
switch:connection:health:{network}               TTL: 60s (refreshed by heartbeat)
lock:bin:{binPrefix}                             TTL: 5s (stampede protection)
```

### Cache Hierarchy

```
L1: Caffeine (JVM, per-instance)    ← microseconds, top 10k BINs
L2: Redis (shared, cross-instance)  ← milliseconds, all cached data
L3: Postgres (source of truth)      ← always correct

Lookup: L1 hit → return
        L1 miss → L2 check → return + populate L1
        L2 miss → L3 query → return + populate L2 + L1
```

### Cache Stampede Prevention

```java
// Redis SETNX lock on cache miss
boolean locked = redisTemplate.opsForValue()
    .setIfAbsent("lock:" + key, "1", Duration.ofSeconds(5));

if (locked) {
    // fetch from DB, populate cache, release lock
} else {
    Thread.sleep(50);   // wait, then retry (will hit cache)
}
```

### Cache Invalidation (Event-Driven)

```java
@KafkaListener(topics = "merchant.config.updated")
public void onMerchantUpdated(MerchantUpdatedEvent event) {
    redisTemplate.delete("merchant:config:" + event.merchantId());
    caffeineCache.invalidate(event.merchantId());
}
```

---

## 12. Kafka Event Topology

### 12.1 Topics

```
transaction.events     partitions: 12  retention: 30 days
settlement.events      partitions: 3   retention: 90 days
chargeback.events      partitions: 3   retention: 365 days
health.events          partitions: 3   retention: 7 days
merchant.config.updated partitions: 3  retention: 7 days
dead.letter            partitions: 3   retention: 30 days
outbox.events          partitions: 6   retention: 7 days (outbox relay)
```

### 12.2 Producer Config

```yaml
spring.kafka.producer:
  acks: all                        # all replicas acknowledge
  retries: 3
  enable-idempotence: true         # exactly-once producer
  compression-type: snappy
```

### 12.3 Consumer Config

```yaml
spring.kafka.consumer:
  auto-offset-reset: earliest
  enable-auto-commit: false        # CRITICAL — manual commit only
  isolation-level: read_committed
```

Manual commit = if processing fails, event stays in topic for retry. Auto-commit = event lost on failure.

### 12.4 Event Envelope

```java
public record DomainEvent<T>(
    String eventId,           // UUID
    String eventType,         // transaction.authorized
    int schemaVersion,        // versioned from day one — consumers don't break on new fields
    String aggregateId,       // transaction UUID
    String aggregateType,     // TRANSACTION
    String producerService,   // payment-switch
    Instant occurredAt,
    T payload
) {}
```

### 12.5 Transaction Event Types

```
transaction.initiated
transaction.authorization_pending
transaction.authorized
transaction.declined
transaction.captured
transaction.reversal_initiated
transaction.reversal_pending
transaction.reversed
transaction.unknown
transaction.refund_initiated
transaction.refunded
transaction.reconciled
transaction.paid_out
```

---

## 13. File Storage

### 13.1 Port Interface

```java
public interface FileStoragePort {
    String store(String filename, byte[] content, FileCategory category);
    byte[] retrieve(String fileKey);
    List<String> listByCategory(FileCategory category, LocalDate date);
}

public enum FileCategory {
    SETTLEMENT, RECONCILIATION_REPORT, CHARGEBACK_EVIDENCE,
    PAYOUT_REPORT, BANK_STATEMENT, TEMP
}

// Swap via config: storage.provider: local | s3
```

### 13.2 S3 Structure

```
payments-platform-{env}/
├── settlements/{yyyy}/{MM}/{dd}/
│   ├── VISA_SETTLEMENT_{yyyyMMdd}_BATCH001.csv
│   ├── MC_SETTLEMENT_{yyyyMMdd}_BATCH001.csv
│   └── NPCI_SETTLEMENT_{yyyyMMdd}_BATCH001.csv
├── reconciliation/{yyyy}/{MM}/{dd}/
│   ├── RECON_{yyyyMMdd}_SUMMARY.json
│   └── RECON_{yyyyMMdd}_EXCEPTIONS.csv
├── chargebacks/
│   └── CB_EVIDENCE_ARN_{arn}.zip
├── payouts/
│   └── PAYOUT_{merchantId}_{yyyyMMdd}.csv
└── statements/
    └── BANK_MT940_{yyyyMMdd}.txt
```

### 13.3 Local vs AWS

```yaml
# Local: LocalStack (Docker)
cloud.aws.s3.endpoint: http://localstack:4566
cloud.aws.credentials.access-key: test
cloud.aws.credentials.secret-key: test

# Production: IAM role on ECS task — no credentials in config
```

---

## 14. Logging & Observability

### 14.1 Structured Logging

Format: JSON via Logback + logstash-logback-encoder (production)
Human-readable pattern format (local dev)

**NEVER LOG:** Full PAN, PIN, CVV, card track data, Field 52 contents, Field 55 ARQC

### 14.2 MDC Context (Set Once, Flows Everywhere)

```java
// Set at entry point — every log line in the request gets this
MDC.put("traceId",       span.getTraceId());
MDC.put("transactionId", transaction.id().toString());
MDC.put("merchantId",    transaction.merchantId().value());
MDC.put("terminalId",    transaction.terminalId());
MDC.put("network",       transaction.network().name());
MDC.put("cardLast4",     panLast4);        // last 4 only
MDC.put("paymentMethod", method.name());
MDC.put("requestId",     UUID.randomUUID().toString());

// Always clear in finally block — thread pool reuse
try { chain.doFilter(...); } finally { MDC.clear(); }
```

### 14.3 Log Levels

```
ERROR:  transaction failures, HSM errors, network disconnections,
        reconciliation mismatches, job failures, dead letter events
        → always triggers ops alert

WARN:   timeout triggered, retry attempt, fraud flag raised,
        limit approaching, reversal sent, race condition detected
        → monitored, alerted if sustained

INFO:   state transitions, batch job milestones, webhook delivered,
        settlement submitted, connection established/dropped
        → audit trail

DEBUG:  ISO 8583 parsing, routing decisions, cache hit/miss, Kafka offset
        → LOCAL ONLY, never production
```

### 14.4 Audit Log Policy

Every state transition writes to `audit_log` table:
- Append-only enforced at DB level (REVOKE UPDATE, DELETE)
- Full sanitized event payload (JSONB)
- Separate from application logs
- Survives log rotation
- 7-year retention (real system), configurable here

### 14.5 Prometheus Metrics

```
payments_transactions_total{status,network,payment_method}
payments_transaction_amount_sum{network}
payments_authorization_latency_ms{network}      ← p50, p95, p99
payments_timeout_total{network}
payments_reversal_total{outcome}
payments_fraud_blocks_total{rule}
payments_webhook_deliveries_total{status}
payments_webhook_retry_total
payments_settlement_batch_total{network,status}
payments_reconciliation_mismatches_total{category}
payments_hsm_operations_total{operation,result}
payments_hsm_latency_ms
payments_switch_connection_status{network}       ← gauge: 1=up, 0=down
payments_cache_hit_rate{cache,level}             ← L1/L2 hit rates
```

### 14.6 Distributed Tracing

OpenTelemetry with auto-instrumentation:
- Traces span across services via HTTP headers + Kafka headers
- Every ISO 8583 round trip is a trace
- HSM calls are spans within switch trace
- Jaeger UI locally (Docker), CloudWatch X-Ray in AWS

### 14.7 Logback Config (Dual Profile)

```xml
<springProfile name="local">
    <!-- human readable, DEBUG level -->
    <pattern>%d{HH:mm:ss} [%thread] %-5level %logger{30}
             [txn=%X{transactionId}] - %msg%n</pattern>
</springProfile>

<springProfile name="staging,prod">
    <!-- JSON, INFO level, all MDC fields included -->
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</springProfile>
```

---

## 15. Health Checks

### 15.1 Spring Actuator

```yaml
management:
  endpoints.web.exposure.include: health,info,metrics,prometheus
  endpoint.health:
    show-details: always
    show-components: always
    group:
      liveness.include: livenessState
      readiness.include: readinessState,db,redis,kafka
```

- `GET /actuator/health/liveness` — is JVM alive? Fail = container restart
- `GET /actuator/health/readiness` — can serve traffic? Fail = removed from ALB, no restart

### 15.2 Custom Health Indicators

**HsmHealthIndicator** — pings SoftHSM2, reports slot count + latency
**NetworkSwitchHealthIndicator** — reports primary/secondary status per network, last heartbeat, latency
**KafkaHealthIndicator** — checks producer connection, record-error-rate metric
**RedisHealthIndicator** — PING → PONG check

### 15.3 Health Response Shape

```json
{
  "status": "UP",
  "components": {
    "db":            { "status": "UP", "details": { "database": "PostgreSQL" } },
    "redis":         { "status": "UP", "details": { "response": "PONG" } },
    "kafka":         { "status": "UP", "details": { "record-error-rate": 0.0 } },
    "hsm":           { "status": "UP", "details": { "provider": "SoftHSM2", "latencyMs": 2 } },
    "networkSwitch": {
      "status": "UP",
      "details": {
        "VISA":       { "primary": "UP", "secondary": "UP", "latencyMs": 38 },
        "MASTERCARD": { "primary": "UP", "secondary": "UP", "latencyMs": 42 },
        "NPCI":       { "primary": "UP", "latencyMs": 45 }
      }
    }
  }
}
```

---

## 16. TDD Strategy

### 16.1 The Workflow

```
RED:      Write failing test first. Describe behaviour.
          Does not compile yet. That is fine.

GREEN:    Write minimum code to pass. Nothing extra.

REFACTOR: Clean up. Test still passes. Repeat.
```

No production code written without a failing test first. No exceptions.

### 16.2 Test Pyramid

```
Unit Tests (domain):       pure Java, no Spring, milliseconds
                           90%+ line coverage enforced by JaCoCo + CI

Unit Tests (use cases):    Mockito mocks, no DB, no Kafka
                           every happy path + error path

Adapter Tests:             @DataJpaTest, @DataRedisTest
                           Testcontainers — real Postgres/Redis

Integration Tests:         @SpringBootTest + Testcontainers
                           full service stack, real infrastructure
                           every major flow end-to-end

Chaos Tests:               WireMock delay/drop scenarios
                           timeout → reversal
                           race condition handling
                           duplicate response

Contract Tests:            Spring Cloud Contract
                           terminal ↔ acquiring
                           acquiring ↔ switch
                           dispatcher ↔ merchant

Load Tests:                k6 (July addition)
                           500 TPS target, p95 < 500ms
```

### 16.3 Test Fixtures

```java
public final class TransactionFixture {
    public static Transaction initiated()             { ... }
    public static Transaction authorizationPending()  { ... }
    public static Transaction authorized()            { ... }
    public static Transaction withStatus(TransactionStatus s) { ... }
    public static Transaction withAmount(String amount) { ... }
}
```

One fixture change propagates everywhere. No copy-pasted test data.

### 16.4 Domain Tests — No Spring Context

```java
@Tag("unit")
class TransactionStateMachineTest {
    // new TransactionStateMachine() — no Spring context
    // runs in milliseconds
    // tests every valid and invalid transition
    // @ParameterizedTest for all transition combinations
}
```

### 16.5 Chaos Tests

```java
@Tag("chaos")
class TimeoutReversalChaosTest {

    @Test
    void shouldTriggerReversalAfterTimeout() {
        wireMockServer.stubFor(post(urlEqualTo("/authorize"))
            .willReturn(aResponse().withFixedDelay(20_000)));

        UUID txnId = switchService.initiateAuthorization(command);

        await().atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> {
                Transaction txn = repo.findById(txnId).orElseThrow();
                assertThat(txn.status()).isIn(REVERSED, UNKNOWN);
            });

        wireMockServer.verify(postRequestedFor(urlEqualTo("/reverse")));
    }

    @Test
    void shouldHandleRaceCondition() {
        // Configure 16s delayed response (after 15s timeout window)
        // Verify: AUTHORIZED never happens after reversal sent
        // Verify: ends in REVERSED or UNKNOWN
    }
}
```

### 16.6 JaCoCo Coverage Enforcement

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <configuration>
        <rules>
            <rule>
                <element>PACKAGE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.90</minimum>   <!-- domain: 90% minimum -->
                    </limit>
                </limits>
                <includes><include>com.payments.domain.*</include></includes>
            </rule>
        </rules>
    </configuration>
</plugin>
```

Domain coverage below 90% = build failure.

### 16.7 Test Tags and CI

```java
@Tag("unit")       // milliseconds — run on every save locally
@Tag("integration") // seconds — run in CI on every commit
@Tag("chaos")       // minutes — run before release
@Tag("load")        // minutes — run weekly
```

CI runs unit + integration only. Chaos and load are manual/scheduled.

---

## 17. Security

### 17.1 PCI-DSS (Simulated)

```
NEVER store full PAN     → SHA-256 hash + last 4 only
NEVER log PAN/PIN/CVV    → MDC has cardLast4 max
NEVER store PIN          → PIN block in memory during translation only
TLS everywhere           → all service-to-service communication
Encryption at rest       → Postgres column encryption (simulated)
Non-root Docker user     → every container runs as non-root
```

### 17.2 HMAC Webhook Signing

```
Algorithm: HMAC-SHA256
Key:       merchant-specific secret (Postgres, never logged)
Input:     raw request body bytes
Output:    hex-encoded digest
Header:    X-Payswiff-Signature: sha256={hex}

Validation: constant-time comparison (prevents timing attacks)
            reject if mismatch
```

### 17.3 API Security

```
Acquiring REST API:    X-Api-Key header
Internal services:     mTLS (mutual TLS)
Postgres:              password auth, least-privilege per service
Redis:                 password auth
S3:                    IAM task role (no credentials in config)
Kafka:                 SASL (local: disabled for simplicity)
```

### 17.4 Secrets Management

```
Local:      .env file (gitignored), .env.example committed
AWS:        AWS Secrets Manager → ECS task definition env vars
CI/CD:      GitHub Actions secrets
NEVER:      secrets in code, config files, or git history
```

---

## 18. Docker — Production Grade

### 18.1 Multi-Stage Build (Every Service)

```dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q   # cache deps layer
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S payments && adduser -S payments -G payments
USER payments

COPY --from=builder /app/target/*.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 \
               -XX:+UseContainerSupport \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 18.2 Docker Compose (Full Local Stack)

```yaml
version: '3.9'
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: payments
      POSTGRES_USER: payments_app
      POSTGRES_PASSWORD: ${DB_PASSWORD:-local_dev_password}
    ports: ["5432:5432"]
    volumes: ["./db/init:/docker-entrypoint-initdb.d", "pgdata:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U payments_app"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    command: redis-server --requirepass ${REDIS_PASSWORD:-local_dev_password}
    ports: ["6379:6379"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on: [zookeeper]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    ports: ["9092:9092"]

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  mailhog:
    image: mailhog/mailhog:latest
    ports: ["1025:1025", "8025:8025"]   # 8025 = web UI

  localstack:
    image: localstack/localstack:latest
    environment:
      SERVICES: s3
      DEFAULT_REGION: ap-south-1
    ports: ["4566:4566"]
    volumes: ["./localstack/init:/etc/localstack/init/ready.d"]

  wiremock:
    image: wiremock/wiremock:3.3.1
    ports: ["8089:8080"]
    volumes: ["./wiremock/mappings:/home/wiremock/mappings"]

  jaeger:
    image: jaegertracing/all-in-one:1.52
    ports: ["16686:16686", "4317:4317"]

  acquiring-service:
    build: ./services/acquiring-service
    ports: ["8000:8000", "8080:8080"]
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/payments
      DB_USERNAME: payments_app
      DB_PASSWORD: ${DB_PASSWORD:-local_dev_password}
      REDIS_HOST: redis
      REDIS_PASSWORD: ${REDIS_PASSWORD:-local_dev_password}
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      UPSTREAM_PROVIDER: wiremock
      HSM_PROVIDER: mock
      SPRING_PROFILES_ACTIVE: local
    depends_on:
      postgres: { condition: service_healthy }
      redis:    { condition: service_healthy }
      kafka:    { condition: service_started }

  payment-switch:
    build: ./services/payment-switch
    ports: ["8100:8100"]
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/payments
      REDIS_HOST: redis
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      UPSTREAM_PROVIDER: wiremock
      HSM_PROVIDER: mock
      SPRING_PROFILES_ACTIVE: local
    depends_on: [postgres, redis, kafka]

  mock-upstream:
    build: ./services/mock-upstream
    ports: ["8001:8001", "8002:8002", "8003:8003", "8004:8004"]

  webhook-dispatcher:
    build: ./services/webhook-dispatcher
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/payments
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      SPRING_PROFILES_ACTIVE: local
    depends_on: [postgres, kafka]

  merchant-simulator:
    build: ./tools/merchant-simulator
    ports: ["9000:9000"]
    environment:
      MERCHANT_WEBHOOK_SECRET: test-webhook-secret-key
      ACQUIRING_SERVICE_URL: http://acquiring-service:8080

  settlement-service:
    build: ./services/settlement-service
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/payments
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      STORAGE_PROVIDER: local
      SPRING_PROFILES_ACTIVE: local
    depends_on: [postgres, kafka, localstack]

  reconciliation-service:
    build: ./services/reconciliation-service
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/payments
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      STORAGE_PROVIDER: local
      SPRING_PROFILES_ACTIVE: local
    depends_on: [postgres, kafka, localstack]

  notification-service:
    build: ./services/notification-service
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      SPRING_MAIL_HOST: mailhog
      SPRING_MAIL_PORT: 1025
      SPRING_PROFILES_ACTIVE: local
    depends_on: [kafka, mailhog]

  chargeback-service:
    build: ./services/chargeback-service
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/payments
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      SPRING_PROFILES_ACTIVE: local
    depends_on: [postgres, kafka]

  frontend:
    build: ./frontend
    ports: ["3000:80"]
    depends_on: [acquiring-service]

volumes:
  pgdata:
```

---

## 19. AWS Deployment — Under ₹5,000/month

### 19.1 Architecture

```
ECS Fargate:         all services as task definitions
RDS PostgreSQL:      db.t3.micro, single-AZ
ElastiCache Redis:   cache.t3.micro
Kafka:               self-managed on t3.small EC2 (cheaper than MSK)
S3:                  standard storage
ALB:                 application load balancer for REST + frontend
ECR:                 container registry
Secrets Manager:     DB passwords, API keys, HSM pins
CloudWatch:          logs (all service stdout → CloudWatch Logs)
SES:                 email delivery
```

### 19.2 Cost Breakdown

```
ECS Fargate (7 services × 0.25vCPU/512MB):    ~₹2,800/month
  settlement + reconciliation: scheduled tasks ~₹100/month (runs 1h/day)
RDS t3.micro single-AZ:                        ~₹800/month
ElastiCache t3.micro:                          ~₹500/month
Kafka on t3.small EC2:                         ~₹600/month
S3 (tiny volume):                              ~₹50/month
ALB:                                           ~₹300/month
ECR + SES + CloudWatch + transfer:             ~₹200/month
─────────────────────────────────────────────────────────
Total:                                         ~₹3,350/month
```

Well under ₹5,000 limit.

### 19.3 Cost Optimisation Rules

```
1. Settlement + reconciliation as ECS scheduled tasks
   Spin up, run job, terminate → ~₹100/month vs ₹600 always-on

2. Spot instances for non-critical services
   webhook-dispatcher, notification-service → ~30% cheaper

3. RDS single-AZ (not Multi-AZ) → saves ₹800/month vs Multi-AZ

4. Self-managed Kafka on EC2 → saves ₹600/month vs MSK kafka.t3.small

5. Stop non-prod resources when not using
   Evenings/weekends → ~40% cost reduction

6. Reserved instances after 3 months confirmed usage
   acquiring-service, payment-switch → ~40% cheaper
```

### 19.4 CI/CD Pipeline (GitHub Actions)

```yaml
name: CI/CD
on:
  push:
    branches: [main, feat/*, fix/*]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: mvn verify -P integration-tests
      - uses: actions/upload-artifact@v4
        with: { name: test-results, path: '**/target/surefire-reports' }

  build-push:
    needs: test
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Login to ECR
        run: aws ecr get-login-password | docker login --username AWS --password-stdin $ECR
      - name: Build and push all service images
        run: docker compose build && docker compose push

  deploy:
    needs: build-push
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Update ECS services
        run: |
          for service in acquiring-service payment-switch webhook-dispatcher \
                         notification-service chargeback-service; do
            aws ecs update-service --cluster payments-platform \
              --service $service --force-new-deployment
          done
```

Tests must pass before build. Build must pass before deploy. Rolling update strategy.

### 19.5 Environment Profiles

```
local:    Docker Compose, WireMock upstream, MockHSM, LocalStack, MailHog
staging:  AWS, WireMock upstream, SoftHSM2, real S3, real SES
prod:     AWS, Razorpay upstream (real money mode), SoftHSM2, real S3, real SES
```

---

## 20. Frontend Specification

### 20.1 Tech Stack

```
React 18 + Vite
Tailwind CSS (custom components — no Bootstrap/MUI templates)
React Query (server state, caching, refetching)
Axios (HTTP client, separate service layer)
Recharts (transaction volume charts)
SSE (real-time feeds — native EventSource)
React Router v6 (SPA routing)
Nginx (serves built app + proxies API calls)
```

### 20.2 Component Architecture

```
src/
├── components/
│   ├── ui/                     ← pure presentational, zero business logic
│   │   ├── Button.jsx
│   │   ├── Badge.jsx           ← status badges with color coding
│   │   ├── StatCard.jsx
│   │   └── DataTable.jsx
│   ├── features/
│   │   ├── simulator/
│   │   │   ├── CardForm.jsx
│   │   │   ├── QRDisplay.jsx
│   │   │   ├── EventLog.jsx    ← SSE stream of ISO 8583 events
│   │   │   └── ScenarioSelector.jsx
│   │   ├── dashboard/
│   │   │   ├── TransactionFeed.jsx
│   │   │   ├── SwitchHealth.jsx
│   │   │   ├── SettlementStatus.jsx
│   │   │   └── StatCards.jsx
│   │   └── ops/
│   │       ├── JobControl.jsx
│   │       ├── ReconciliationDetail.jsx
│   │       └── DeadLetterQueue.jsx
│   └── layout/
│       ├── Sidebar.jsx
│       └── Header.jsx
├── hooks/
│   ├── useTransactions.js      ← React Query
│   ├── useSSE.js               ← EventSource wrapper
│   ├── useQRSession.js         ← polls /qr/status + SSE
│   └── useSwitchHealth.js      ← polls /actuator/health
├── services/                   ← API calls only, no React
│   ├── transactionService.js
│   ├── qrService.js
│   ├── settlementService.js
│   └── opsService.js
└── pages/
    ├── Simulator.jsx
    ├── Dashboard.jsx
    └── Ops.jsx
```

UI components never make direct API calls.
Components → hooks → service layer → Axios → backend.
Three layers, each with one job.

### 20.3 App 1 — Payment Simulator

**Purpose:** Demo tool. Card form + QR mode. Live ISO 8583 event log.
**Route:** `/simulator`
**Design:** Dark theme (Stripe-inspired), desktop-first

```
┌─────────────────────────────────────────────────────────────┐
│  💳 Payment Simulator                    [Card] [QR]        │
├─────────────────────┬───────────────────────────────────────┤
│                     │                                       │
│  Card Payment       │  Live Event Log                       │
│  ─────────────────  │  ─────────────────────────────────    │
│  PAN: [____________]│  14:30:22.441  INITIATED              │
│  Expiry: [____]     │  14:30:22.512  ISO 8583 0100 built    │
│  CVV: [___]         │  14:30:22.891  MAC verified ✓         │
│  Amount: [₹______]  │  14:30:22.901  ARQC verified ✓        │
│                     │  14:30:22.912  Fraud score: LOW       │
│  Scenario:          │  14:30:23.104  0100 → Visa switch     │
│  [Normal Purchase▼] │  14:30:23.891  0110 ← response        │
│                     │  14:30:23.892  Field 39: 00 APPROVED  │
│  [    Pay Now    ]  │  14:30:23.901  AUTHORIZED ✓           │
│                     │  14:30:23.920  Kafka event published  │
│                     │  14:30:23.945  Webhook delivered      │
│                     │                                       │
├─────────────────────┴───────────────────────────────────────┤
│  Recent Transactions                                        │
│  ₹6,000 · MERCH0000999 · AUTHORIZED ✓ · VISA · 14:30:23   │
│  ₹1,200 · MERCH0000999 · DECLINED ✗ · MASTERCARD · 14:28  │
└─────────────────────────────────────────────────────────────┘
```

**QR mode:**
```
┌──────────────────────────────────────────────┐
│  Amount: [₹ 6000     ]  [Generate QR]        │
│                                               │
│  ┌────────────────┐  Status: ⏳ Waiting       │
│  │  [QR CODE IMG] │                           │
│  │  (scannable)   │  Scan with any UPI app    │
│  │                │  Expires in: 4:32         │
│  └────────────────┘                           │
│                                               │
│  ✅ Payment received! ₹6,000                  │
│  NPCI Txn: NPCI20260511XXXX                  │
│  Paid at: 14:30:23                           │
└──────────────────────────────────────────────┘
```

**Scenario Selector Options:**
- Normal Purchase
- Timeout + Reversal (watch race condition handling)
- Decline (NSF)
- Decline (Stolen Card)
- Partial Reversal
- Duplicate Request (idempotency test)

### 20.4 App 2 — Merchant Dashboard

**Purpose:** Simulates what a merchant sees in a real payments dashboard.
**Route:** `/dashboard`

```
┌─────────────────────────────────────────────────────────────┐
│  Today                                                      │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐  │
│  │₹78,24,600 │ │  1,847    │ │  98.4%    │ │  423ms    │  │
│  │Volume     │ │Transactions│ │ Success   │ │Avg Latency│  │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘  │
├─────────────────────────────────────────────────────────────┤
│  [Line Chart: Transaction Volume Last 24h]                  │
├──────────────────────────────────┬──────────────────────────┤
│  Live Transactions               │  Switch Health          │
│  ─────────────────────────────  │  ──────────────────────  │
│  ₹6,000 VISA     AUTHORIZED ✅  │  VISA      🟢 🟢        │
│  ₹1,200 MC       DECLINED  ❌   │  MASTERCARD 🟢 🟢       │
│  ₹500   UPI      AUTHORIZED ✅  │  NPCI UPI  🟢           │
│  ₹8,000 VISA     CAPTURED  ✅   │                          │
│  [Load more...]                  │  Settlement             │
│                                  │  ──────────────────────  │
│                                  │  ✅ 10 May COMPLETE      │
│                                  │  ✅ 09 May COMPLETE      │
│                                  │  Next: Tonight 23:30    │
└──────────────────────────────────┴──────────────────────────┘
│  Generate QR                                                │
│  [₹ amount input]  [Generate]  → shows QR inline           │
└─────────────────────────────────────────────────────────────┘
```

Transaction row → click → full detail drawer:
- All ISO 8583 fields (sanitized — no PAN, last 4 only)
- State machine timeline
- Webhook delivery status
- Kafka event trail

### 20.5 App 3 — Ops Dashboard

**Purpose:** Internal monitoring and batch job control.
**Route:** `/ops`

```
┌─────────────────────────────────────────────────────────────┐
│  Ops Dashboard                                              │
├─────────────────────────────────────────────────────────────┤
│  [Line Chart: TPS last 24h]  Success: 98.4%  Errors: 1.6%  │
├──────────────────┬──────────────────────────────────────────┤
│  Batch Jobs      │  Queues                                  │
│  ────────────── │  ──────────────────────────────────────  │
│  Settlement 23:30│  Dead Letter:    2 messages  [View]      │
│  ✅ 10 May [Run] │  Exception Queue: 1 mismatch  [View]     │
│  Recon     07:00 │                                          │
│  ✅ 11 May [Run] │  Recent Alerts                           │
│                  │  ──────────────────────────────────────  │
│  Switch Health   │  ⚠️ 14:30 DLQ: webhook failed          │
│  ────────────── │  ℹ️ 11:22 Visa reconnected (4s down)    │
│  VISA    🟢 38ms │  ✅ 23:30 Settlement: 1,624 txns        │
│  MC      🟢 42ms │  ✅ 07:45 Recon: 0 exceptions           │
│  NPCI    🟢 45ms │                                          │
└──────────────────┴──────────────────────────────────────────┘
```

[Run Now] on batch jobs → calls `POST /settlement/trigger` or `POST /reconciliation/trigger`

### 20.6 Real-Time Updates

```javascript
// SSE hook — transaction feed
const useSSE = (url) => {
    const [events, setEvents] = useState([]);
    useEffect(() => {
        const source = new EventSource(url);
        source.onmessage = (e) => setEvents(prev => [JSON.parse(e.data), ...prev].slice(0, 50));
        return () => source.close();
    }, [url]);
    return events;
};

// QR status — polls every 2s OR uses SSE
const useQRSession = (txnRef) => {
    return useQuery(['qr', txnRef], () => qrService.getStatus(txnRef), {
        refetchInterval: (data) => data?.status === 'PENDING' ? 2000 : false,
        enabled: !!txnRef
    });
};
```

---

## 21. QR Code Specification

### 21.1 UPI String Format

```
upi://pay?
  pa={vpa}     required — payee VPA (merchant_999@payswiff)
  pn={name}    required — payee name (URL encoded)
  mc={mcc}     optional — merchant category code
  tr={txnRef}  optional — transaction reference (dynamic QR only)
  am={amount}  optional — amount (dynamic QR, absent in static)
  cu=INR       optional — currency
  tn={note}    optional — transaction note
```

### 21.2 Generation (ZXing)

```java
// ZXing 3.5.2
QRCodeWriter writer = new QRCodeWriter();
BitMatrix matrix = writer.encode(
    upiString,
    BarcodeFormat.QR_CODE,
    300, 300,                                      // pixels
    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)  // 15% damage recovery
);
byte[] png = MatrixToImageWriter.toBufferedImage(matrix)
    → ImageIO.write → ByteArrayOutputStream → base64
```

### 21.3 QR Session (Redis)

```
Key:   qr:session:{txnRef}
Value: { merchantId, terminalId, amount, currency,
         status: PENDING|COMPLETED|EXPIRED|FAILED,
         createdAt, expiresAt, npciTxnId }
TTL:   5 minutes (configurable: qr.session.ttl-minutes)

Expiry: Redis auto-deletes → late credits → exception queue → auto-refund → ops alert
```

---

## 22. Fee Waterfall

### 22.1 Calculation

```
Gross Transaction Amount
  - Interchange Fee              → issuing bank (via network)
  - Network Assessment Fee       → Visa/MC/RuPay
  - Payswiff MDR                 → Payswiff revenue
  = Net to Merchant
  - Reserve Withholding (5%)     → merchant reserve account
  = Payout Amount                → NEFT to merchant bank
```

### 22.2 Interchange Rates (Simplified)

```
Visa Credit (Consumer):   1.80%
Visa Debit:               0.90%
Mastercard Credit:        1.75%
Mastercard Debit:         0.85%
RuPay Credit:             1.00%
RuPay Debit:              0.60%
UPI:                      0.00%
```

### 22.3 Network Assessment Fees

```
Visa:       0.14%
Mastercard: 0.13%
RuPay:      0.06%
UPI:        0.00%
```

### 22.4 Payswiff MDR

```
Default: 1.50% (configurable per merchant)
Minimum: ₹2.00 per transaction
```

---

## 23. Configuration Reference

### 23.1 Key Properties Per Service

```yaml
# acquiring-service
server.port: 8080
iso8583.port: 8000
upstream.provider: ${UPSTREAM_PROVIDER:wiremock}   # wiremock|razorpay|npci
hsm.provider: ${HSM_PROVIDER:mock}                  # mock|softhsm
db.provider: ${DB_PROVIDER:postgres}                # postgres|mongo|csv
storage.provider: ${STORAGE_PROVIDER:local}         # local|s3

# payment-switch
timeout.authorization-ms: 15000
timeout.reversal-ms: 15000
fraud.enabled: true
routing.default-network: VISA

# settlement-service
settlement.cron: "0 30 23 * * *"
settlement.networks: VISA,MASTERCARD,NPCI
settlement.chunk-size: 1000

# reconciliation-service
reconciliation.mismatch-alert-threshold: 5

# notification-service
notification.ops-email: ${OPS_EMAIL}
notification.from-email: noreply@payments-platform.local

# qr sessions
qr.session.ttl-minutes: 5
```

### 23.2 Environment Variables

```
DB_URL                   jdbc:postgresql://host:5432/payments
DB_USERNAME              payments_app
DB_PASSWORD              from Secrets Manager
REDIS_HOST               redis host
REDIS_PASSWORD           from Secrets Manager
KAFKA_BOOTSTRAP_SERVERS  kafka:9092
UPSTREAM_PROVIDER        wiremock|razorpay|npci
HSM_PROVIDER             mock|softhsm
HSM_PKCS11_LIB           /usr/lib/softhsm/libsofthsm2.so
HSM_TOKEN_PIN            from Secrets Manager
STORAGE_PROVIDER         local|s3
AWS_REGION               ap-south-1
S3_BUCKET                payments-platform-{env}
SENDGRID_API_KEY         from Secrets Manager
OPS_EMAIL                ops@payments-platform.local
RAZORPAY_KEY_ID          from Secrets Manager (prod only)
RAZORPAY_KEY_SECRET      from Secrets Manager (prod only)
```

---

## 24. What Is Stubbed / What Is Real

### 24.1 Never Stub (Core to Interview Story)

```
ISO 8583 parsing          real jPOS, real field definitions
SoftHSM2 crypto           real ARQC/PIN/MAC operations
Transaction state machine all 20 states, all valid transitions
Idempotency               real Redis with TTL
Timeout + reversal        real timeout monitor, real reversal flow
Race condition handling   real detection and discard logic
Webhook HMAC              real HMAC-SHA256
Spring Batch settlement   real chunk-based batch, real file generation
Three-way reconciliation  real matching algorithm, real mismatch categories
Kafka topology            real producers/consumers, manual offset commit
Outbox pattern            real DB + Kafka consistency
Domain ports/adapters     real hexagonal architecture enforced by ArchUnit
```

### 24.2 Stubbed (Document as Future Work)

```
Real NPCI connection      → mock REST service (legally cannot do as individual)
Real Visa/MC connection   → jPOS mock switch
Real bank MT940 statement → generated mock file
TC40/SAFE fraud reports   → stubbed parser + logged
EMI conversion            → interface defined, not implemented
BBPS integration          → out of scope
Multi-currency            → INR only
Full merchant KYC flow    → hardcoded test merchants in DB seed
Grafana dashboards        → Prometheus metrics exposed, dashboard July addition
ML fraud scoring          → port defined now, service built post-June (see §24.3)
```

### 24.3 Post-June ML Fraud Scoring (Pluggable Architecture)

**What it is:**
A standalone `fraud-scoring-service` that exposes a REST or gRPC endpoint. It takes a
transaction context and returns a probability score (0.0–1.0). Internally it can use
any ML approach: rule-based ensemble, gradient boosting, LangChain RAG over fraud
knowledge base, or a fine-tuned LLM. The payment-switch does not care — it calls a port.

**Why pluggable from day one:**
The `FraudEngine` domain service will accept an `Optional<FraudScoringPort>` via
constructor injection. If the bean is absent (no `fraud-scoring-service` configured),
the optional is empty and the engine runs rule-based only. No code changes needed
when the service is added.

**Port interface (defined in domain now, implemented post-June):**
```java
// domain/port/outbound/FraudScoringPort.java
public interface FraudScoringPort {
    /**
     * Returns an ML risk probability score in [0.0, 1.0].
     * Must complete within the budget or return empty.
     * Never called on the hot path — fire-and-forget or pre-cached.
     */
    Optional<BigDecimal> score(FraudScoringContext context, Duration budget);
}

public record FraudScoringContext(
    PanHash panHash,
    Money amount,
    String merchantCategory,
    PaymentNetwork network,
    PaymentMethod method,
    Instant transactionTime
) {}
```

**FraudScore result type (carries ML score from day one):**
```java
public record FraudScore(
    RiskLevel ruleBasedLevel,         // always populated — sprint default
    Optional<BigDecimal> mlRiskScore, // empty during sprint, populated post-June
    List<String> triggeredRules       // which rules fired
) {
    public RiskLevel effectiveLevel() {
        // Rule-based always wins on BLOCK
        if (ruleBasedLevel == BLOCK) return BLOCK;
        // ML can elevate MEDIUM → HIGH or HIGH → BLOCK post-June
        return mlRiskScore
            .map(score -> elevate(ruleBasedLevel, score))
            .orElse(ruleBasedLevel);
    }

    private RiskLevel elevate(RiskLevel base, BigDecimal mlScore) {
        if (mlScore.compareTo(new BigDecimal("0.85")) > 0) return BLOCK;
        if (mlScore.compareTo(new BigDecimal("0.60")) > 0 && base == MEDIUM) return HIGH;
        return base;
    }
}
```

**Integration strategy (post-June):**
```
Call model: payment-switch calls fraud-scoring-service ASYNCHRONOUSLY
            via CompletableFuture with 10ms budget (p95 of rule-based is <2ms)
            If score arrives in time → merged into FraudScore
            If timeout → Optional.empty() → rule-based score used
            Never blocks the main authorization path

Kafka alternative: payment-switch publishes transaction.fraud_context event
                  fraud-scoring-service consumes, scores, publishes back
                  payment-switch correlates via Redis (TTL 500ms)
                  Use this if model latency is > 20ms p95
```

**fraud-scoring-service stack (post-June):**
```
Python FastAPI + scikit-learn / XGBoost (phase 1)
LangChain RAG over fraud knowledge base (phase 2 — post-July)
  Knowledge base: past declined transactions, fraud patterns, TC40 reports
  Vector store: Chroma or Pinecone
  LLM: Claude Haiku (cheap, fast) for reasoning over retrieved patterns
Spring Boot sidecar for Kafka bridge (optional)
```

**What to do NOW (sprint):**
1. Define `FraudScoringPort` interface in `domain/port/outbound/` — empty interface
2. Define `FraudScoringContext` and ensure `FraudScore` has `Optional<BigDecimal> mlRiskScore`
3. Wire `Optional<FraudScoringPort>` into `FraudEngine` constructor — if absent, no-op
4. No implementation needed — `AdapterConfig.java` simply does not define the bean
5. This costs ~30 lines of code and zero future refactoring when the service is added

---

## 25. Non-Negotiable Rules

### 25.1 Code Rules

```
1. No business logic in controllers or Kafka consumers
   Controllers: validate input → call use case → return response
   Consumers: deserialize → call use case → commit offset

2. No domain types in HTTP responses or Kafka payloads
   Use separate request/response DTOs
   Mapper translates at adapter boundary

3. Every state transition through TransactionStateMachine
   No direct status field updates
   Invalid transition = exception = 500 = ops alert

4. Every monetary amount is BigDecimal, scale 2, HALF_UP rounding
   Zero exceptions. Ever.

5. Every external call has explicit timeout
   HSM: 500ms
   Upstream network: 15s
   Webhook delivery: 5s
   Database: 3s (HikariCP connection-timeout)

6. No secrets in code, config files, or git history
   .env gitignored, .env.example committed
   AWS: Secrets Manager only

7. Every service independently deployable
   No coordinated deployments
   Schema changes via backward-compatible Flyway migrations
   Kafka messages schema-versioned from day one

8. Constructor injection only
   No @Autowired on fields. Ever.
   All dependencies are final.

9. Domain module has zero external dependencies
   If you add Spring to domain/pom.xml — architecture is broken
   ArchUnit enforces this in CI

10. PAN never in plaintext after terminal
    Stored as SHA-256 hash. Logged as last 4 only.
    PIN never outside HSM boundary.
```

### 25.2 Git Rules

```
Commit format: conventional commits
  feat: add dynamic QR generation
  fix: handle reversal race condition
  test: add chaos tests for timeout scenarios
  docs: update CLAUDE.md with chargeback flow
  refactor: extract fee calculation to domain service

Branch naming:
  main         always deployable, always green CI
  feat/{name}  feature branches
  fix/{name}   bug fixes

PR strategy: squash merge to main
No direct commits to main
```

---

## 26. Demo Script (Interviews)

**Setup:** `docker compose up` → wait 60 seconds → open `localhost:3000`

**Demo 1 — Card Transaction (45 seconds)**
```
Open Simulator → Card mode
Enter: PAN 4539148803436467, Expiry 12/28, CVV 123, Amount ₹6000
Scenario: Normal Purchase
Click Pay
Watch: event log shows ISO 8583 fields, ARQC verified, fraud LOW,
       AUTHORIZED in 800ms
Switch to Dashboard → see transaction appear in live feed
Click row → see full ISO 8583 detail drawer
```

**Demo 2 — Dynamic QR (45 seconds)**
```
Open Simulator → QR mode
Enter: ₹500
Click Generate QR
Real scannable QR appears (UPI deep link encoded)
Scan with phone → UPI app opens, amount pre-filled
Approve payment
Dashboard: transaction appears in live feed instantly (SSE)
MailHog (localhost:8025): merchant webhook email arrived
```

**Demo 3 — Timeout + Reversal (60 seconds)**
```
Simulator → Card mode → Scenario: Timeout + Reversal
Click Pay
Watch event log: 0100 sent → 15 seconds → timeout fires
Watch: reversal sent, REVERSAL_PENDING, REVERSED
Dashboard: transaction shows REVERSED with full timeline
Explain: race condition handling — if 0110 arrives after reversal
```

**Demo 4 — Settlement + Reconciliation (45 seconds)**
```
Ops Dashboard → Batch Jobs panel
Click [Run Now] on Settlement
Watch: Spring Batch job starts, progress visible
S3 (LocalStack): settlement CSV files appear
Click [Run Now] on Reconciliation
Watch: three-way match runs, RECON summary appears
MailHog: ops job completion email with stats
```

**Demo 5 — System Health (30 seconds)**
```
Ops Dashboard → Switch Health panel
Shows: VISA primary/secondary UP, latency 38ms
Kill mock-upstream container
Watch: health indicator turns red within 60s
Email in MailHog: switch disconnected alert
Restart container → watch reconnect with exponential backoff
```

---

## 27. Project Context (For Claude)

### 27.1 Who Is Building This

Sohom — software engineer currently in QA automation role at Payswiff (joined April 2026).
Target: SDE2 at Zepto/Swiggy/Zomato or Juspay/Razorpay by November 2026.
Background: B.E. ECE from BITS Pilani, Hyderabad. Prior: Qualcomm (via Zentree Labs),
Achala IT Solutions, CleverTap internship.

### 27.2 Timeline Constraint

May 11 → June 30: 36 hours/week. Project only. No DSA. Ship everything.
July onwards: DSA + system design weekdays. Project enhancements weekends only.

### 27.3 Coding Preferences

- DSA/system design questions: mock interview format (clarify, constraints, think aloud, STAR)
- Code responses: function implementations only, no main methods, no imports, no boilerplate
  unless explicitly asked
- Prefer targeted edits over full rewrites
- Always follow all principles in this document
- Never suggest shortcuts that violate hexagonal architecture or SOLID
- BigDecimal for all money — never suggest double or float

### 27.4 What "Done" Means

A feature is done when:
1. TDD — failing test written first
2. Implementation makes test pass
3. Integration test covers the flow
4. Structured logging with MDC context
5. Health indicator updated if infrastructure changed
6. Kafka event published if state changed
7. Audit log written if transaction state changed
8. No secrets in code
9. BigDecimal for all amounts
10. ArchUnit tests still pass

---

*End of CLAUDE.md*
*Version: 2.0*
*Last updated: May 2026*
*Author: Sohom — payments-platform project*

<!-- dgc-policy-v11 -->
# Dual-Graph Context Policy

This project uses a local dual-graph MCP server for efficient context retrieval.

## MANDATORY: Always follow this order

1. **Call `graph_continue` first** — before any file exploration, grep, or code reading.

2. **If `graph_continue` returns `needs_project=true`**: call `graph_scan` with the
   current project directory (`pwd`). Do NOT ask the user.

3. **If `graph_continue` returns `skip=true`**: project has fewer than 5 files.
   Do NOT do broad or recursive exploration. Read only specific files if their names
   are mentioned, or ask the user what to work on.

4. **Read `recommended_files`** using `graph_read` — **one call per file**.
   - `graph_read` accepts a single `file` parameter (string). Call it separately for each
     recommended file. Do NOT pass an array or batch multiple files into one call.
   - `recommended_files` may contain `file::symbol` entries (e.g. `src/auth.ts::handleLogin`).
     Pass them verbatim to `graph_read(file: "src/auth.ts::handleLogin")` — it reads only
     that symbol's lines, not the full file.
   - Example: if `recommended_files` is `["src/auth.ts::handleLogin", "src/db.ts"]`,
     call `graph_read(file: "src/auth.ts::handleLogin")` and `graph_read(file: "src/db.ts")`
     as two separate calls (they can be parallel).

5. **Check `confidence` and obey the caps strictly:**
   - `confidence=high` -> Stop. Do NOT grep or explore further.
   - `confidence=medium` -> If recommended files are insufficient, call `fallback_rg`
     at most `max_supplementary_greps` time(s) with specific terms, then `graph_read`
     at most `max_supplementary_files` additional file(s). Then stop.
   - `confidence=low` -> Call `fallback_rg` at most `max_supplementary_greps` time(s),
     then `graph_read` at most `max_supplementary_files` file(s). Then stop.

## Token Usage

A `token-counter` MCP is available for tracking live token usage.

- To check how many tokens a large file or text will cost **before** reading it:
  `count_tokens({text: "<content>"})`
- To log actual usage after a task completes (if the user asks):
  `log_usage({input_tokens: <est>, output_tokens: <est>, description: "<task>"})`
- To show the user their running session cost:
  `get_session_stats()`

Live dashboard URL is printed at startup next to "Token usage".

## Rules

- Do NOT use `rg`, `grep`, or bash file exploration before calling `graph_continue`.
- Do NOT do broad/recursive exploration at any confidence level.
- `max_supplementary_greps` and `max_supplementary_files` are hard caps - never exceed them.
- Do NOT dump full chat history.
- Do NOT call `graph_retrieve` more than once per turn.
- After edits, call `graph_register_edit` with the changed files. Use `file::symbol` notation (e.g. `src/auth.ts::handleLogin`) when the edit targets a specific function, class, or hook.

## Context Store

Whenever you make a decision, identify a task, note a next step, fact, or blocker during a conversation, call `graph_add_memory`.

**To add an entry:**
```
graph_add_memory(type="decision|task|next|fact|blocker", content="one sentence max 15 words", tags=["topic"], files=["relevant/file.ts"])
```

**Do NOT write context-store.json directly** — always use `graph_add_memory`. It applies pruning and keeps the store healthy.

**Rules:**
- Only log things worth remembering across sessions (not every minor detail)
- `content` must be under 15 words
- `files` lists the files this decision/task relates to (can be empty)
- Log immediately when the item arises — not at session end

## Session End

When the user signals they are done (e.g. "bye", "done", "wrap up", "end session"), proactively update `CONTEXT.md` in the project root with:
- **Current Task**: one sentence on what was being worked on
- **Key Decisions**: bullet list, max 3 items
- **Next Steps**: bullet list, max 3 items

Keep `CONTEXT.md` under 20 lines total. Do NOT summarize the full conversation — only what's needed to resume next session.
