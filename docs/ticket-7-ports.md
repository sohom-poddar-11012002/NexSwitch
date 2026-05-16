# Ticket #7 — All Ports (Inbound + Outbound + FraudScoringPort)

## What
29 Java 21 files in `com.nexswitch.domain.port.inbound/`, `com.nexswitch.domain.port.outbound/`,
and `com.nexswitch.domain.model/` that define every contract between the domain core and the
outside world — five inbound use case interfaces, five commands, nine outbound port interfaces,
two port-specific types, and seven result/model types.

## Why
Closes #7. Hexagonal architecture has no meaning without explicit ports. Without these
interfaces, adapters (REST controllers, Kafka consumers, JPA repositories) have nowhere to
plug in. Every subsequent ticket depends on these contracts: #8 (TransactionStateMachine)
implements domain logic against them; #12 (JPA entities) implements `TransactionRepository`;
#17 (acquiring-service) calls `ProcessPaymentUseCase`. Defining ports now locks the
architectural boundary before any adapter code is written.

## Design Decisions

- **Commands as Java 21 records**: Records enforce immutability structurally — no setters,
  no accidental mutation between validation and use. Compact constructors centralise all
  `requireNonNull` and range checks so callers cannot construct an invalid command. ArchUnit
  `PortsArchitectureTest` verifies at build time that every `*Command` in the inbound
  package is a record.

- **Inbound use cases as single-method interfaces**: Each interface has exactly one `execute`
  method — not a fat interface with multiple operations. This is Interface Segregation applied
  directly: a controller that handles refunds only needs to depend on `ProcessRefundUseCase`,
  not on an interface that also knows about QR generation. ArchUnit verifies all `*UseCase`
  types are interfaces.

- **Result types as sealed interfaces (Java 21)**: `AuthorizationResult`, `RefundResult`,
  `ReversalResult`, `QRGenerationResult`, `ReconciliationResult`, and `SettlementResult` are
  all sealed. This forces exhaustive `switch` pattern matching at every call site — the
  compiler rejects any code that handles "approved" but forgets "declined". Business failures
  (decline, timeout, reversal already sent) are encoded as result variants, not exceptions.
  Exceptions are reserved for programming errors.

- **Result types in `domain/model/` not `domain/port/`**: Sealed result types like
  `AuthorizationResult` are domain concepts, not adapter plumbing. The domain service that
  processes authorisation returns one; the domain state machine transitions based on one.
  Placing them in `model/` makes them available to both ports and services without creating
  a dependency on the port layer itself.

- **`FraudScoringPort` is Optional by design**: The port is defined now but no adapter
  implements it during the sprint. `FraudEngine` will accept `Optional<FraudScoringPort>` —
  absent bean means rule-based scoring only, zero code changes needed post-June when
  the ML service is added (see CLAUDE.md §24.3). The port returns `Optional<BigDecimal>`
  so callers never throw on timeout.

- **`FraudScore` carries `mlRiskScore` from day one**: `Optional<BigDecimal>` is always
  empty during the sprint. `effectiveLevel()` encodes the merge logic so the domain service
  never has conditional `if ml present` blocks scattered through it — the record owns the
  rule. BLOCK always wins regardless of ML signal; ML can only elevate (never demote).

- **`ReversalCommand` cross-field validation**: `reversalAmount` must not exceed
  `originalAmount` — this is a domain invariant (you cannot reverse more than you authorised).
  Validating in the compact constructor means no service or adapter ever needs to re-check it.

- **`ReconciliationCommand.networks` uses `Set.copyOf()`**: Defensive copy prevents the
  caller from mutating the set after construction and changing the command's semantics
  mid-flight. `Set.of()` and `Set.copyOf()` both reject nulls, giving an early failure.

- **`FileCategory` and `SettlementResult` in `port/outbound/`**: These are only meaningful
  in the context of their respective ports. `FileCategory` is an enum the `FileStoragePort`
  uses to route files to correct S3 prefixes; `SettlementResult` is returned by
  `SettlementPort`. No domain model class needs them, so they live with their port.

- **ArchUnit `PortsArchitectureTest` enforces structure at build time**: Four rules —
  use cases are interfaces, ports/repositories are interfaces, commands are records, ports
  have no infrastructure dependencies. Violations fail the CI build, not a code review comment.

## Test Coverage

| Test class | Tests | What is verified |
|---|---|---|
| `FraudScoreTest` | 11 | `effectiveLevel()`: BLOCK always wins, ML >0.85→BLOCK, ML >0.60+MEDIUM→HIGH, ML >0.60+LOW stays LOW, boundary at 0.85 (exclusive), immutable rules list, null ruleBasedLevel throws |
| `FraudScoringContextTest` | 7 | All 6 fields null-checked in compact constructor, valid construction succeeds |
| `AuthorizationResultTest` | 5 | Each variant carries expected fields; exhaustive switch pattern matches all 4 permits |
| `ResultTypesTest` | 12 | `RefundResult`, `ReversalResult`, `QRGenerationResult`, `ReconciliationResult` — each variant carries expected fields; exhaustive pattern matching on all permits |
| `AuthorizationCommandTest` | 5 | Null transactionId/merchantId throws; zero/negative amount throws; valid command constructs |
| `RefundCommandTest` | 5 | Null transactionId/amount, blank reason, zero amount throw; valid constructs |
| `QRGenerationCommandTest` | 5 | Null merchantId/terminalId, blank orderId, zero amount throw; valid constructs |
| `ReversalCommandTest` | 5 | Null transactionId, reversalAmount > originalAmount, zero reversalAmount throw; partial reversal valid |
| `ReconciliationCommandTest` | 5 | Null date/networks, empty networks throw; networks set is immutable after construction |
| `OutboundPortTypesTest` | 5 | `FileCategory.values()` and `valueOf()`; `SettlementResult` variants carry expected fields; exhaustive switch |
| `PortsArchitectureTest` | 4 | ArchUnit: use cases are interfaces, ports/repositories are interfaces, commands are records, ports have no infrastructure deps |

Total new tests: **69** (of 254 total in domain module)

## How to Verify

```bash
mvn test -pl domain
# Expected: BUILD SUCCESS, Tests run: 254+, JaCoCo ≥90% on all packages
```
