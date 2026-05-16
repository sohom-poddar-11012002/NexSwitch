# Code Learning Map

> One-line bridge from each source file to the CS concept it demonstrates.
> Mirrors the `// LEARN:` comments in the code. Use this as a quick-scan study guide.

---

## Domain — Exceptions

| File | Concept | One-line insight |
|---|---|---|
| `InvalidStateTransitionException` | StructuredException | Typed context fields (transactionId, from, to) make logs machine-parseable |
| `RoutingException` | UncheckedException | Configuration errors are non-recoverable; unchecked avoids try-catch clutter |

---

## Domain — Core Models

| File | Concept | One-line insight |
|---|---|---|
| `Transaction` | AggregateRoot | Static factory (initiate), guard+throw, raises DomainEvents; no setters |
| `TransactionStatus` | StateMachine | VALID_TRANSITIONS Map gives O(1) canTransitionTo; enum keys prevent typos |
| `AuthorizationResult` | SealedInterface | Compiler enforces exhaustive switch; no default case = no missed outcome |
| `NetworkRoute` | SealedInterface | Routing decision as value; OnUs/Ibl/Gateway are mutually exclusive tiers |
| `QRGenerationResult` | SealedInterface | Generated/Failed exhaustive; caller cannot ignore failure path |
| `ReconciliationResult` | SealedInterface | Completed/Failed; mismatchCount in Completed is the key reconciliation KPI |
| `RefundResult` | SealedInterface | Initiated/Failed; refundId in Initiated enables async tracking |
| `ReversalResult` | Idempotency | AlreadyReversed permit is the domain's way of expressing "safe to retry" |
| `BinInfo` | ValueObject | Immutable record; nfsEligible drives NFS→PRIMARY→SECONDARY routing tier |
| `MerchantProfile` | RichDomainModel | isActive() and limit checks live here, not in service; domain carries behaviour |
| `ChargebackRecord` | RichDomainModel | Chargeback has its own lifecycle (responseDeadline, status) independent of Transaction |
| `FeeBreakdown` | ValueObject | Fee waterfall result; all components immutable in one record for audit trail |
| `FraudScore` | OptionalField | mlRiskScore is Optional because ML port is not wired pre-June |
| `FraudScoringContext` | ContextObject | Aggregates inputs for a service call; avoids long parameter lists |
| `FraudVelocityData` | ValueObject | Pre-computed Redis counters; separates data fetch from rule evaluation |
| `QRSession` | DomainTTL | expiresAt is a domain concept; Redis TTL is an implementation detail of the adapter |
| `ReconciliationRun` | AggregateState | Reconciliation run tracks its own lifecycle independent of individual transactions |
| `SettlementBatch` | AggregateRoot | Nested Status enum keeps batch lifecycle self-contained |
| `RoutingRule` | ValueObject | BIN prefix matching encapsulated here; matches() keeps routing logic out of RoutingEngine |
| `PaymentMethod` | SmartEnum | isCard() / isUpi() keep routing logic out of switch statements in callers |
| `PaymentNetwork` | SmartEnum | isInternational() drives DCC eligibility check without instanceof |
| `RiskLevel` | DomainEnum | BLOCK level triggers rejection before network round-trip; saves latency on hot path |

---

## Domain — Events

| File | Concept | One-line insight |
|---|---|---|
| `DomainEvent` | DomainEvent | Generic envelope with schemaVersion for Avro evolution compatibility |
| `TransactionInitiatedEvent` | DomainEvent | Published inside Transaction.initiate(); adapter publishes to Kafka after save |
| `TransactionAuthorizedEvent` | DomainEvent | Carries authorizationCode so downstream services don't re-query |
| `TransactionDeclinedEvent` | DomainEvent | responseCode is the ISO 8583 field 39 value from the network |
| `ReversalInitiatedEvent` | DomainEvent | Triggers timeout-reversal saga in payment-switch service |
| `ChargebackReceivedEvent` | DomainEvent | Triggers chargeback evidence workflow in chargeback service |

---

## Domain — Value Objects

| File | Concept | One-line insight |
|---|---|---|
| `Money` | ValueObject | BigDecimal+Currency pair; scale 2 HALF_UP enforced at construction; add() checks currency |
| `PanHash` | PCI-DSS | SHA-256 applied at terminal boundary; raw PAN never stored or logged after fromRawPan() |
| `MerchantId` | WrapperType | Distinct type prevents passing TerminalId where MerchantId expected (type safety) |
| `TerminalId` | WrapperType | 8-char constraint matches ISO 8583 field 41 terminal ID format |
| `SystemTraceAuditNumber` | CorrelationId | STAN links ISO 8583 0100 request to 0110 response; resets to 1 after 999999 |
| `AuthorizationCode` | NetworkAssigned | 6-char approval code from issuer; absent until AUTHORIZED state |
| `AcquirerReferenceNumber` | GlobalReference | 23-digit ARN is how networks identify transactions for chargebacks and recon |

---

## Domain — Inbound Ports

| File | Concept | One-line insight |
|---|---|---|
| `ProcessPaymentUseCase` | DependencyInversion | Application layer depends on this interface, not on domain service impl |
| `ProcessRefundUseCase` | DependencyInversion | Use case interface is the inbound boundary of the hexagon |
| `ProcessReversalUseCase` | DependencyInversion | Adapter calls this; domain never depends on adapter |
| `GenerateQRUseCase` | DependencyInversion | QR generation use case; ZXing lives in adapter, not here |
| `ReconcileUseCase` | DependencyInversion | Batch job calls this interface; domain doesn't know about Spring Batch |
| `AuthorizationCommand` | CommandObject | All inputs for one use case; emvData/pinBlock as byte[] for binary ISO 8583 fields |
| `QRGenerationCommand` | CommandObject | Validated at boundary (amount > 0, orderId not blank) before hitting domain |
| `RefundCommand` | CommandObject | refundAmount <= original enforced in domain service, not here |
| `ReversalCommand` | CommandObject | reversalAmount <= originalAmount validated at construction |
| `ReconciliationCommand` | CommandObject | Set<PaymentNetwork> allows per-network reconciliation runs |

---

## Domain — Outbound Ports

| File | Concept | One-line insight |
|---|---|---|
| `TransactionRepository` | RepositoryPort | Domain defines the interface; JPA @Entity is invisible to domain |
| `AuthorizationPort` | AdapterPort | Network authorization abstracted; Visa adapter and MockAdapter implement same interface |
| `HsmPort` | SecurityBoundary | All HSM operations defined here; domain knows operations, not key handles |
| `FraudScoringPort` | OptionalPort | Returns Optional; absent when ML service not wired (pre-June) |
| `WebhookDispatchPort` | AdapterPort | HMAC signing happens in adapter; domain only calls dispatch() |
| `NotificationPort` | AdapterPort | Email/SMS template resolution is adapter concern; domain passes templateName |
| `FileStoragePort` | AdapterPort | S3 key management hidden; domain uses FileCategory enum as logical namespace |
| `RefundPort` | AdapterPort | Refund request routed to original network; domain doesn't know which |
| `SettlementPort` | AdapterPort | Batch file submission to network; domain submits SettlementBatch record |
| `SettlementResult` | SealedInterface | Submitted/Failed; networkBatchId in Submitted is the reconciliation key |
| `FileCategory` | DomainEnum | Logical file namespace; adapter maps to S3 bucket prefix |

---

## Domain — Services

| File | Concept | One-line insight |
|---|---|---|
| `TransactionStateMachine` | StateMachine | Single entry point for all state transitions; raises domain event on every change |
| `FraudEngine` | RuleEngine | O(1) velocity rule checks on hot auth path; ML scoring is async optional |
| `RoutingEngine` | StrategyPattern | Three-tier: NFS → PRIMARY → SECONDARY; BIN drives tier selection |
| `FeeWaterfallCalculator` | FeeWaterfall | MDR → interchange → assessment → acquirer margin; BigDecimal throughout |
| `QRSessionManager` | DomainService | TTL and STAN generation are domain concepts; Redis is the adapter |
| `LuhnValidator` | LuhnAlgorithm | Validates PAN checksum at terminal boundary before network round-trip |

---

## Adapters — Persistence

| File | Concept | One-line insight |
|---|---|---|
| `TransactionEntity` | OptimisticLocking | @Version field; JPA increments on every UPDATE, throws on stale read |
| `MerchantEntity` | JpaEntity | ORM model; separate from MerchantProfile domain record to allow schema evolution |
| `TerminalEntity` | JpaEntity | String PK matches ISO 8583 field 41 (8-char terminal ID) |
| `JpaTransactionRepository` | JpaSpecificationExecutor | Type-safe dynamic query composition; avoids string-concatenated JPQL |
| `JpaMerchantRepository` | SpringDataJpa | Derived query findByStatus() generates SQL at startup; no runtime string parsing |
| `JpaTerminalRepository` | SpringDataJpa | @Query for JOIN-heavy queries; derived method names for simple lookups |
| `TransactionMapper` | MapStruct | All-default-method pattern; Transaction fluent accessors break @Mapping path resolution |
| `MerchantMapper` | MapStruct | MapStruct generates implementation at compile time; zero reflection at runtime |
| `PostgresTransactionRepository` | AdapterPattern | Implements domain port; domain calls save(Transaction), never touches @Entity |

---

## Test Infrastructure

| File | Concept | One-line insight |
|---|---|---|
| `IntegrationTestBase` | TestcontainersSingleton | Static containers shared across all test classes; withReuse(true) survives JVM restarts |
| `TransactionFixture` | TestFixture | Hardcoded MERCHANT_ID/TERMINAL_ID match V11 seed; no INSERT needed in integration tests |
| `MerchantFixture` | TestFixture | active() mirrors V11 seed row; tests read from Postgres seeded by Flyway, not by fixture |
| `BinInfoFixture` | TestFixture | rupayNfs() is NFS-eligible; used to test NFS routing divergence from VISA path |
| `Iso8583MessageFixture` | ISO8583 | Map<Integer,String> models variable bitmap; field presence = key in map, absence = key missing |
