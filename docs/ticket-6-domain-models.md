# Ticket #6 — Domain Models

## What
Domain model layer: two enums (`PaymentMethod`, `PaymentNetwork`), `TransactionStatus`
with 20 states and a valid-transitions map, `Transaction` class with builder and domain
event support, and seven supporting types: `QRSession`, `RoutingRule`, `BinInfo`,
`MerchantProfile`, `ChargebackRecord`, `SettlementBatch`, `ReconciliationRun`.

## Why
Closes #6. Domain models are the core types that every use case, port, adapter, and
mapper works with. Without them nothing can be wired — ports have no types to pass,
adapters have nothing to map to, and services have nothing to operate on.

## Design Decisions

- **`Transaction` uses a builder, not a record** — `Transaction` has ~15 fields, many
  optional (`authorizationCode`, `arn`, `responseCode`). Java records require all fields
  in the canonical constructor; a 15-argument constructor is unreadable and forces callers
  to pass `null` for every optional field. A builder lets each field be set by name and
  optional fields be omitted entirely.

- **Domain events as a mutable `List` on `Transaction`** — `pullDomainEvents()` drains
  the list atomically. The application layer calls `save()` first, then pulls and publishes
  the events. If the DB save fails the events are never pulled — no ghost Kafka messages
  with no corresponding DB record.

- **`with*` methods return new instances** — `Transaction` is effectively immutable.
  `transaction.withStatus(AUTHORIZED)` returns a new `Transaction` object with every field
  copied from the original except `status`. The original object is untouched. This makes
  concurrent reads safe and prevents accidental in-place mutation.

- **`QRSession` and `ReconciliationRun` are final classes with builders, not records** —
  Both need `withStatus()` and `withResults()` mutation patterns (a record's fields are
  final; you can't return a modified copy from a compact constructor). The rest
  (`RoutingRule`, `BinInfo`, `MerchantProfile`, `SettlementBatch`, `ChargebackRecord`)
  are pure records — immutable by design, no mutation needed.

- **Status enums are nested** — `QRSession.Status`, `MerchantProfile.Status`,
  `ChargebackRecord.Status`, `SettlementBatch.Status`, `ReconciliationRun.Status` are all
  defined inside their parent class. This keeps related types co-located and avoids a
  proliferation of tiny top-level enum files that are meaningless out of context.

- **`ChargebackRecord.totalLiability()`** — encapsulates `amount + chargebackFee`
  addition inside the record so callers never do `Money` arithmetic inline. Every
  chargeback costs the merchant the disputed amount plus a ₹350 processing fee; this
  method returns the combined figure as a single `Money`.

- **`TransactionStatus` owns the valid-transitions map** — the map is a static final
  `Map<TransactionStatus, Set<TransactionStatus>>` defined on the enum itself. The
  `TransactionStateMachine` service (ticket #8) looks up this map rather than hardcoding
  transitions in its own logic. One place to change, one place to test.

---

## File-by-File Reference

### `PaymentMethod.java`
**What it is:** An enum listing how a payment was physically made.

**Values:** `CARD_CHIP`, `CARD_CONTACTLESS`, `CARD_MAGSTRIPE`, `UPI_DYNAMIC_QR`,
`UPI_STATIC_QR`, `UPI_COLLECT`.

**Why it matters:** Routing, fraud rules, HSM operations, and fee calculation all differ
by payment method. Contactless under ₹5000 skips PIN and CVM. UPI has 0% MDR. Chip
card requires ARQC verification. An enum makes exhaustive `switch` expressions possible
— the compiler forces you to handle every case rather than falling through silently.

**Key methods:**
- `isCard()` — returns true for all three card variants; used in fraud velocity rules
  that apply only to card transactions.
- `isUpi()` — returns true for all three UPI variants; used in fee waterfall to apply
  0% interchange and network assessment.

---

### `PaymentNetwork.java`
**What it is:** An enum for which card/payment network processes the transaction.

**Values:** `VISA`, `MASTERCARD`, `RUPAY`, `UPI_NPCI`.

**Why it matters:** Each network has its own ISO 8583 TCP connection, its own
interchange rate table, its own settlement file format, and its own response code
meanings. The routing engine selects a network based on BIN lookup; all downstream
logic branches on this value.

**Key methods:**
- `isUpi()` — UPI transactions go over REST to NPCI, not ISO 8583 TCP. Used in the
  routing engine and connection manager to pick the right adapter.
- `isInternational()` — currently `VISA` and `MASTERCARD`; used in fraud rules
  (first international transaction on a domestic-pattern PAN is a high-risk signal)
  and in interchange rate lookup (international rates differ from domestic).

---

### `TransactionStatus.java`
**What it is:** An enum with all 20 states a transaction can occupy, plus a hardcoded
map of which state transitions are valid.

**The 20 states:**
```
INITIATED → AUTHORIZATION_PENDING → AUTHORIZED → CAPTURED → SETTLEMENT_PENDING
         → RECONCILED → PAID_OUT

                                  → DECLINED
                                  → REVERSAL_PENDING → REVERSED
                                                     → UNKNOWN → (resolved later)
                                  → REFUND_INITIATED → REFUND_PENDING → REFUNDED
                                                                       → REFUND_FAILED
RECONCILED/PAID_OUT → CHARGEBACK_RECEIVED → CHARGEBACK_CONTESTED
                    → CHARGEBACK_EVIDENCE_SUBMITTED → CHARGEBACK_WON / CHARGEBACK_LOST
```

**Why the transitions map matters:** Without it, any code anywhere could set
`transaction.status = RECONCILED` on a freshly `INITIATED` transaction. The transitions
map is a static final `Map<TransactionStatus, Set<TransactionStatus>>` on the enum.
Any attempt to go from state A to state B that isn't in the map throws
`InvalidStateTransitionException` → HTTP 500 → ops alert. Data integrity is enforced
at the domain level, not just by convention.

**Terminal states** (no valid outbound transitions): `REVERSED`, `REFUNDED`,
`REFUND_FAILED`, `CHARGEBACK_WON`, `CHARGEBACK_LOST`. Once a transaction reaches one
of these, it cannot be moved further.

---

### `Transaction.java`
**What it is:** The central domain object — represents one payment transaction from
initiation through to settlement, refund, or chargeback resolution.

**Key fields:** `id` (UUID), `stan` (STAN value object), `rrn` (retrieval reference),
`arn` (ARN value object, assigned by acquirer), `terminalId`, `merchantId`
(value objects), `panHash` (never raw PAN), `network`, `paymentMethod`, `amount`
(Money), `status` (TransactionStatus), `authorizationCode` (optional), `responseCode`,
`riskScore`, `idempotencyKey`, `createdAt`, `updatedAt`.

**Why a builder, not a record:** ~15 fields, many optional. A 15-argument constructor
is unreadable. A builder lets you write:
```java
Transaction.builder()
    .stan(stan).merchantId(merchantId).amount(amount)
    .network(VISA).paymentMethod(CARD_CHIP)
    .build();
```
and omit `authorizationCode`, `arn`, `responseCode` until they are known.

**`with*` immutability pattern:**
- `withStatus(TransactionStatus)` — returns new Transaction, status changed.
- `withAuthorizationCode(AuthorizationCode)` — returns new Transaction, auth code set.
- `withArn(AcquirerReferenceNumber)` — returns new Transaction, ARN set.
- The original object is always untouched. This makes the object safe to pass across
  threads without locking and prevents accidental partial mutation.

**Domain events:**
- Internal `List<Object> domainEvents` starts empty.
- State-changing `with*` methods add an event: e.g. `withStatus(AUTHORIZED)` adds a
  `TransactionAuthorizedEvent`.
- `pullDomainEvents()` drains the list and returns it. Called by the application layer
  after the DB save succeeds, before publishing to Kafka.
- If the DB save fails and an exception is thrown, `pullDomainEvents()` is never called,
  so no Kafka messages are published for a transaction that was never persisted.

---

### `QRSession.java`
**What it is:** Represents a live UPI QR payment session. Stored in Redis with a 5-minute
TTL; not persisted to Postgres.

**Lifecycle:**
1. Merchant calls `POST /qr/generate` → `QRSession` created, `status = PENDING`, stored
   in Redis with key `qr:session:{txnRef}`, TTL 5 minutes.
2. Customer scans QR, pays via UPI app.
3. NPCI sends credit notification → acquiring service looks up session by `txnRef`,
   validates amount → updates `status = COMPLETED`, deletes session from Redis.
4. If TTL expires before payment: Redis auto-deletes. Any late credit arriving finds no
   session → goes to exception queue → auto-refund → ops alert.

**Key fields:** `txnRef`, `merchantId`, `terminalId`, `amount` (Money), `status`,
`createdAt`, `expiresAt`, `npciTxnId` (populated on completion).

**Key methods:**
- `isExpired()` — `Instant.now().isAfter(expiresAt)`. Used before processing any
  incoming credit notification.
- `withStatus(Status)` — returns a new `QRSession` with status changed (immutability).

**Nested `Status` enum:** `PENDING`, `COMPLETED`, `EXPIRED`, `FAILED` — defined inside
`QRSession` so the relationship is obvious at a glance.

---

### `RoutingRule.java`
**What it is:** A record defining one routing rule — "if BIN prefix starts with `4`,
route to `VISA`".

**Why it exists:** The `RoutingEngine` (ticket #10) holds a list of these, sorted by
priority, and picks the first rule that matches the incoming BIN prefix. Having them as
immutable records makes the list safe to cache in Redis and share across threads.

**Key fields:** `binPrefixPattern` (regex or prefix string), `network`
(PaymentNetwork), `priority` (int — lower number = checked first).

**Key method:** `matches(String binPrefix)` — returns true if this rule applies to the
given BIN prefix. The routing engine calls this on each rule in priority order and uses
the first match.

---

### `BinInfo.java`
**What it is:** A record holding what the first 6–8 digits of a card number tell us
about the card.

**Why it matters:** Every card transaction begins with a BIN lookup. The result
determines: which network to route to, which interchange rate to apply, whether the
card is domestic or international, and which issuer issued it. This record is cached
heavily — Caffeine L1 (microseconds) → Redis L2 (milliseconds) → Postgres L3 (source
of truth).

**Key fields:** `binPrefix` (6–8 chars), `network` (VISA/MASTERCARD/RUPAY),
`issuerName` (e.g. "HDFC Bank"), `cardType` (CREDIT/DEBIT), `cardProduct`
(e.g. "Signature"), `countryCode` (default "IN").

**Validation:** BIN prefix must be 6–8 characters — shorter or longer values are not
valid BINs.

---

### `MerchantProfile.java`
**What it is:** A record holding a merchant's configuration — everything the acquiring
service needs to validate a transaction for that merchant.

**Why it exists:** Every transaction is checked against merchant rules before being
forwarded upstream: is the merchant active? Does the amount exceed the per-transaction
limit? Is today's volume under the daily limit? These checks need the merchant's config,
not a live DB query on every message. The record is cached in Redis (TTL 5 minutes,
invalidated on `merchant.config.updated` Kafka event).

**Key fields:** `id` (MerchantId), `name`, `mcc` (merchant category code — 4 digits,
e.g. `5411` for grocery), `status`, `mdrPercentage` (BigDecimal, e.g. `0.0150` = 1.5%),
`perTxnLimit` (Money), `dailyLimit` (Money), `reservePercentage` (BigDecimal),
`webhookUrl`, `webhookSecret`.

**Key method:** `isActive()` — returns true only when `status == ACTIVE`. Suspended
or terminated merchant → transaction declined immediately, before any upstream call.

**Nested `Status` enum:** `ACTIVE`, `SUSPENDED`, `TERMINATED`.

---

### `ChargebackRecord.java`
**What it is:** A record representing one chargeback filed by a cardholder against a
transaction via their issuing bank and network.

**Lifecycle:** `RECEIVED → CONTESTED → EVIDENCE_SUBMITTED → WON / LOST`. Also
`RECEIVED → ACCEPTED` (merchant concedes immediately).

**Key fields:** `transactionId` (UUID of the original transaction), `arn`
(to match with network), `network`, `reasonCode` (e.g. Visa dispute code `10.4`),
`amount` (Money — the disputed amount), `chargebackFee` (Money — fixed ₹350),
`status`, `responseDeadline` (Visa: 30 days, MC: 45 days from receipt),
`evidenceS3Key` (path to the ZIP evidence package when submitted).

**Key method:** `totalLiability()` — returns `amount.add(chargebackFee)`. Every
chargeback debits the merchant both the disputed amount and the processing fee.
Encapsulating the addition here means no caller ever does inline `Money` arithmetic
to compute what the merchant owes.

**Nested `Status` enum:** `RECEIVED`, `ACCEPTED`, `CONTESTED`, `EVIDENCE_SUBMITTED`,
`WON`, `LOST`.

---

### `SettlementBatch.java`
**What it is:** A record representing one settlement batch — a group of captured
transactions sent to a network for settlement on a given day.

**One batch per network per day:** e.g. all Visa transactions captured on 10 May are
grouped into one batch, generating `VISA_SETTLEMENT_20260510_BATCH001.csv` on S3.

**Key fields:** `id` (UUID), `network` (PaymentNetwork), `batchDate` (LocalDate),
`transactionCount` (int), `grossAmount` (Money — sum of all transaction amounts in
batch), `status`, `batchFileS3Key` (S3 path of the generated CSV),
`networkBatchId` (the reference ID returned by the mock network on submission).

**Nested `Status` enum:** `GENERATED`, `VALIDATED`, `SUBMITTED`, `CONFIRMED`, `FAILED`.

---

### `ReconciliationRun.java`
**What it is:** Tracks one execution of the three-way reconciliation process — matching
the switch's Postgres records against the network's settlement confirmation file and the
bank's MT940 statement.

**Why a class, not a record:** It is updated progressively as the reconciliation Spring
Batch job runs. `withResults(matched, mismatch, unknownResolved)` returns a new instance
with updated counts — a record's fields are final so the builder/`with*` pattern on a
class is the right tool here.

**Key fields:** `runDate` (LocalDate), `totalTransactions` (int), `matchedCount` (int),
`mismatchCount` (int), `unknownResolvedCount` (int), `status`, `summaryS3Key`
(path to `RECON_{date}_SUMMARY.json`), `exceptionsS3Key`
(path to `RECON_{date}_EXCEPTIONS.csv`), `startedAt` / `completedAt` (Instant).

**Mismatch categories tracked (in the exceptions CSV, not directly on this record):**
`MISSING_IN_NETWORK`, `MISSING_IN_SWITCH`, `MISSING_IN_BANK`, `AMOUNT_MISMATCH`,
`DUPLICATE_IN_NETWORK`, `UNKNOWN_RESOLVED`.

**Nested `Status` enum:** `RUNNING`, `COMPLETE`, `FAILED`.

---

## Test Coverage

| Class | Tests | Key scenarios |
|---|---|---|
| `TransactionStatus` | 17 | All 30 valid transitions (parameterized), 13 invalid transitions throw exception, terminal states have no outbound, self-transition blocked |
| `PaymentMethod` | 4 | Enum size, `isCard()` and `isUpi()` for each value |
| `PaymentNetwork` | 4 | Enum size, `isUpi()`, `isInternational()` |
| `Transaction` | 10 | Builder constructs correctly, `withStatus` returns new object with original unchanged, `withAuthCode`, `withArn`, domain events raised and drained via `pullDomainEvents`, null guards on required fields |
| `QRSession` | 7 | `isExpired()` true/false, `withStatus` immutability, status enum values, null `txnRef` guard |
| `BinInfo` | 4 | Construction, null guards, BIN prefix 6–8 char validation |
| `MerchantProfile` | 6 | `isActive()` true, `isActive()` false when suspended, null guards, status enum |
| `RoutingRule` | 5 | `matches()` true, `matches()` false, null guards, negative priority rejected |
| `ChargebackRecord` | 4 | Construction, `totalLiability()` = amount + fee, status enum, null `transactionId` guard |
| `SettlementBatch` | 4 | Construction, status enum, negative count rejected, null date rejected |
| `ReconciliationRun` | 4 | Zero-init counts, `withResults` returns new instance with original unchanged, status enum, null date rejected |

**Total: 184 tests (96 from #5 value objects + 88 new from #6). JaCoCo ≥ 90% — all checks passed.**

## How to Verify

```bash
mvn test -pl domain
# Expected: Tests run: 184, Failures: 0, Errors: 0
# Expected: All coverage checks have been met.
# Expected: BUILD SUCCESS
```
