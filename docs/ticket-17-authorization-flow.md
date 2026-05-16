# Ticket #17 — Authorization Flow (8-Step Validation Chain)

## What
Implemented the full card authorization pipeline in `AuthorizationService`, covering every validation step a payment switch performs between receiving an ISO 8583 0100 message and forwarding to the card network.

## Why
This is the core of the switch. Every card tap at a terminal flows through this pipeline. Getting the step ordering, fail-fast behavior, and idempotency right prevents revenue leakage (BIN fraud), duplicate charges (STAN retransmission), and chargebacks (limit breaches, ARQC failures).

## Design Decisions

**Step ordering is security-critical.** BIN lookup and idempotency fire before any database reads — this keeps the fast-fail path cheap. Terminal/merchant checks follow, then amount limits. ARQC and fraud only run after the transaction row is created (so we have a record of every attempt, including declined ones). Network is last.

**Fail-open on fraud ML.** `FraudScoringPort` returns `Optional<BigDecimal>`. If the ML service is unavailable or times out within 500ms, `Optional.empty()` means "no signal — forward to network." Blocking on ML timeout would degrade auth success rate, hurting merchants more than fraud would.

**bin6 added to `AuthorizationCommand`.** The PAN is hashed at the inbound adapter boundary (PanHash), losing BIN information. BIN (first 6 digits) is PCI-safe to pass in plaintext — only the full PAN requires hashing. So bin6 is extracted from Field 2 before hashing and included in the command.

**AuthorizationService is not `@Service`.** The domain module has zero Spring dependencies. Spring wiring happens in `AdapterConfig` using a `@Bean` method that accepts domain port interfaces as parameters — Spring injects whichever `@Component`/`@Repository` implementation is on the classpath. Swapping mock → real adapters requires only a config property change.

**Sealed switch on result.** The `switch (result)` over `AuthorizationResult` is exhaustive — the compiler enforces that every case (`Approved`, `Declined`, `Unknown`, `Blocked`) is handled. No default branch needed; this prevents silent fallthrough when new result types are added.

## 8-Step Pipeline

| Step | Guard | Response Code |
|------|-------|---------------|
| 1 | BIN lookup — unknown BIN | 57 — Transaction not permitted |
| 2 | Idempotency — duplicate STAN+terminal | 94 — Duplicate transmission |
| 3 | Terminal active check | 58 — Terminal not permitted |
| 4 | Merchant active check | 57 — Transaction not permitted |
| 5 | Per-transaction limit check | 61 — Exceeds withdrawal limit |
| 6 | ARQC verification (EMV only) | 82 — ARQC verification failed |
| 7 | Fraud score ≥ 0.80 (fail-open) | Blocked (no ISO code) |
| 8 | Network authorization (Visa/MC/RuPay) | Field 39 from network |

## Adapter Implementations (all mock for local dev)

| Port | Adapter | Behavior |
|------|---------|----------|
| `BinLookupPort` | `MockBinLookupAdapter` | In-memory table: Visa 411111/424242, MC 555555/510510, RuPay 508528/606985 |
| `IdempotencyPort` | `RedisIdempotencyAdapter` | `SET NX EX` — atomic SETNX with 5-minute TTL |
| `TerminalRepository` | `PostgresTerminalRepository` | JPA `findById` → `Terminal` domain record |
| `MerchantRepository` | `PostgresMerchantRepository` | JPA `findById` → `MerchantProfile` via `MerchantMapper` |
| `HsmPort` | `MockHsmAdapter` | `verifyArqc` always returns true; real SoftHSM2 wired in staging |
| `FraudScoringPort` | `MockFraudScoringAdapter` | Always returns `Optional.empty()` (fail-open, no ML blocking) |
| `AuthorizationPort` | `MockNetworkAuthAdapter` | Always approves with authCode `000000` |

## Test Coverage

- `AuthorizationServiceTest` — 12 unit tests (Mockito), one per validation failure + golden path
- `AuthorizationCommandTest` — 5 validation tests (null checks, positive amount enforcement)
- All 494 domain tests pass; JaCoCo ≥ 90% line coverage enforced

## How to Verify

```bash
mvn test -pl domain -am                   # 494 tests, BUILD SUCCESS
mvn test -pl adapters -am                 # 28 adapter tests, BUILD SUCCESS
mvn compile -pl adapters,application -am  # clean compile, no errors
```

To exercise the full path end-to-end, wire the ISO 8583 inbound adapter to `ProcessPaymentUseCase` (ticket #18).
