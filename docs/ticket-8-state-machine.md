# Ticket #8 — TransactionStateMachine

## What
A pure-Java domain service that enforces all valid state transitions for `Transaction`
and raises a domain event on every successful transition.

## Why
Closes #8. Every state change in the system must flow through `TransactionStateMachine`
so that invalid transitions are caught at the domain boundary before any persistence or
Kafka event occurs. Centralising transition logic prevents ad-hoc `withStatus()` calls
from bypassing the 14-entry transition map.

## Design decisions

- **Delegation to `TransactionStatus.canTransitionTo()`** — the transition map lives on
  the enum (ticket #6), so `TransactionStateMachine` has one job: null-check → validate
  → update → raise event. No duplication of transition logic.

- **Immutability preserved** — `transition()` returns a new `Transaction` via
  `withStatus()`; the original is never mutated. The raised event lives on the returned
  instance, not the original, so callers cannot accidentally pull stale events.

- **Domain event naming convention** — `"transaction." + status.name().toLowerCase()`
  produces consistent, deterministic names (e.g. `transaction.authorization_pending`,
  `transaction.reversal_pending`). The application layer maps these to Kafka topic events
  when publishing.

- **`InvalidStateTransitionException`** carries `transactionId`, `from`, and `to` status,
  giving the ops alert and error log enough context without wrapping a separate DTO.

- **ArchUnit Rule 4 scoped to non-test classes** — the rule that domain services may only
  depend on `com.payments.domain..*` is now scoped with `.haveNameNotMatching(".*Test")`
  so test classes in the same package can import AssertJ and JUnit without false violations.

- **`TransactionFixture`** added to `com.payments.domain.fixture` (test scope only) — a
  single factory for building `Transaction` instances in any status, eliminating builder
  boilerplate across all service-layer tests.

## Test coverage

- **70 tests** across `TransactionStateMachineTest`
- All 30 valid transitions → returned status matches target
- Representative 13 valid transitions → domain event present with correct name
- Immutability: original transaction unchanged after `transition()` call
- All preserved fields (id, merchantId, amount, createdAt) unchanged after transition
- 13 invalid transitions → `InvalidStateTransitionException` thrown
- Exception carries correct `from`, `to`, `transactionId`, and message text
- Self-transitions throw
- All 6 terminal states → any outbound transition throws
- Null `transaction` and null `target` → `NullPointerException`
- ArchUnit Rule 4 still GREEN (62 violations resolved by adding `.haveNameNotMatching`)

## How to verify

```bash
mvn test -pl domain
# → 324 tests, 0 failures

mvn verify -pl domain
# → All coverage checks have been met (≥90%)
```
