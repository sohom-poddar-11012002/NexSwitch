# Ticket #6 — Domain Models

## What
Domain model layer: two enums (PaymentMethod, PaymentNetwork), TransactionStatus with 20 states and
valid transitions map, Transaction class with builder and domain event support, and six supporting
records: QRSession, RoutingRule, BinInfo, MerchantProfile, ChargebackRecord, SettlementBatch,
ReconciliationRun.

## Why
Closes #6. Required by the hexagonal architecture — domain models are the core types that every
use case, port, adapter, and mapper works with. No models = nothing to wire.

## Design Decisions
- **Transaction uses a builder, not a record** — Transaction has ~15 fields, many optional
  (authorizationCode, arn, responseCode). Java records require all fields in the canonical
  constructor; a builder keeps construction readable and optional fields nullable without
  factory method explosion.
- **Domain events as a mutable List on Transaction** — `pullDomainEvents()` drains the list
  atomically. The application layer calls save first, then pull and publish — guarantees no
  ghost events if save fails.
- **`with*` methods return new instances** — Transaction is effectively immutable; every state
  change produces a new object copied from the original with one field changed. The original
  is untouched.
- **Supporting records use compact constructors** — QRSession and ReconciliationRun need mutation
  (`withStatus`, `withResults`) so they are plain final classes with builders. The rest
  (RoutingRule, BinInfo, MerchantProfile, SettlementBatch, ChargebackRecord) are pure records.
- **Status enums are nested** — `QRSession.Status`, `MerchantProfile.Status` etc. keeps related
  types co-located and avoids a proliferation of top-level enum files.
- **`ChargebackRecord.totalLiability()`** — encapsulates amount + fee addition inside the record
  so callers never do Money arithmetic inline.

## Test Coverage
| Class                | Tests | Key scenarios |
|----------------------|-------|---------------|
| TransactionStatus    | 17    | All 30 valid transitions (parameterized), 13 invalid, terminal states, self-transition |
| PaymentMethod        | 4     | Enum size, isCard(), isUpi() per value |
| PaymentNetwork       | 4     | Enum size, isUpi(), isInternational() |
| Transaction          | 10    | Builder, withStatus immutability, withAuthCode, withArn, domain events raise/pull, null guards |
| QRSession            | 7     | isExpired(), withStatus immutability, status enum, null txnRef guard |
| BinInfo              | 4     | Construction, null guards, BIN prefix length validation (6–8 chars) |
| MerchantProfile      | 6     | isActive(), suspended false, null guards, status enum |
| RoutingRule          | 5     | matches() true/false, null guards, negative priority |
| ChargebackRecord     | 4     | Construction, totalLiability(), status enum, null transactionId |
| SettlementBatch      | 4     | Construction, status enum, negative count, null date |
| ReconciliationRun    | 4     | Zero-init counts, withResults immutability, status enum, null date |

**Total: 184 tests (96 from #5 value objects + 88 new). JaCoCo ≥ 90% — all checks passed.**

## How to Verify
```bash
mvn test -pl domain
# Expected: Tests run: 184, Failures: 0, Errors: 0
# Expected: All coverage checks have been met.
```
