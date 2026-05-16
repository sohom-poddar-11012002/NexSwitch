# Ticket #11 — Domain Events

## What
A typed domain-event infrastructure for the Transaction aggregate: a generic `DomainEvent<T>`
envelope and five event-payload records covering every state transition that produces a
side-effect visible to the application layer (authorization, decline, reversal, chargeback,
and initial creation). `Transaction` gains four semantic aggregate methods and one static
factory that each validate the transition, apply the state change, and raise a typed event.
`TransactionStateMachine.transition()` is updated to raise `DomainEvent<TransactionStatus>`
(generic) for all other transitions.

## Why
Closes #11. Required by the transactional outbox pattern (§2.9) and the Kafka topology
(§12.5): the application layer drains `pullDomainEvents()` after saving the aggregate and
publishes the events. Typed payloads (authCode, responseCode, amount) let consumers act
without looking up the transaction again.

## Design Decisions
- **Package**: `com.nexswitch.domain.model.event` — events are domain model types, not port types.
- **Envelope fields**: `eventId` (UUID), `eventType`, `schemaVersion` (always 1, versioned from day one), `aggregateId`, `aggregateType`, `producerService` ("domain"), `occurredAt`, typed `payload`. Matches §12.4.
- **`DomainEvent.of()` factory**: generates the UUID, timestamps now, sets schemaVersion=1 and producerService="domain". Direct constructor is still available for test doubles.
- **Aggregate methods vs state machine**: `Transaction.authorize()`, `decline()`, `initiateReversal()`, `receiveChargeback()` own both the validation and the typed event — this is the "Tell Don't Ask" path (§2.11). `TransactionStateMachine.transition()` is the generic path (no authCode/responseCode), still raises a `DomainEvent<TransactionStatus>` so existing state-machine tests need no structural change.
- **`Transaction.initiate()` static factory**: the one place where `TransactionInitiatedEvent` is raised; the builder is kept for tests and adapters (no events at build time).
- **`pullDomainEvents()` is destructive**: returns and clears — exactly one application-layer call per save, no double-publishing.
- **No Spring/Kafka in event classes**: zero infrastructure imports — ArchUnit rule 1 still green.

## Test Coverage
| Class | Test File | Cases |
|---|---|---|
| `DomainEvent<T>` | `DomainEventTest` | unique eventId, schemaVersion=1, producerService="domain", occurredAt≈now, typed payload, record equality |
| `Transaction` aggregate methods | `TransactionDomainEventTest` | initiate factory, authorize, decline, initiateReversal, receiveChargeback — each tests: return status, event type, payload fields, original-not-mutated, invalid-state throws |
| `TransactionStateMachine` | `TransactionStateMachineTest` | updated to assert `DomainEvent::eventType` not raw string |
| `Transaction` builder path | `TransactionTest` | updated raiseEvent and pullDomainEvents tests to use typed events |

Total: **445 tests**, all green. JaCoCo ≥90% gate passes.

## How to Verify
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-26.jdk/Contents/Home \
  mvn clean test -pl domain
# Expected: BUILD SUCCESS, 445 tests, All coverage checks have been met
```
