# Ticket #9 — FraudEngine

## What
A pure-Java domain service that evaluates all seven fraud rules against a transaction
and returns a `FraudScore` (rule-based level + triggered rule names).

## Why
Closes #9. Inline fraud scoring on the authorization hot path — <50ms target.
Blocking decisions must happen in the domain before forwarding to the upstream network.

## Design decisions

- **Pure function, no ports** — `FraudEngine.score(context, velocity)` takes
  pre-fetched velocity data as a value object (`FraudVelocityData`). The application
  layer fetches Redis counters and impossible-travel flag before calling the engine.
  The domain stays framework-free and testable with `new FraudEngine()`.

- **`FraudVelocityData` record** — carries the five pre-computed signals:
  `panTransactionsLast5Min`, `panTransactionsLastHour`, `terminalTransactionsLastHour`,
  `isFirstTransactionOnPan`, `isImpossibleTravel`. Impossible travel is pre-computed
  by the adapter (Redis last-location lookup + distance calculation) — the engine just
  reads a boolean.

- **All rules evaluated, no short-circuit** — every rule is checked even if BLOCK
  fires early. All violated rule names are collected in `triggeredRules` for the audit
  log and ops alerts. The final level is the maximum severity across all fired rules.

- **`RiskLevel.ordinal()` for `max()`** — enum ordinal order matches severity order
  (LOW < MEDIUM < HIGH < BLOCK), so a single `max()` helper handles severity comparison
  without a switch or if-chain.

- **Rule name constants are `public static final`** — callers (application layer, ops
  dashboards) can reference them without stringly-typed literals.

- **High-risk MCCs as an immutable `Set.of()`** — O(1) lookup, clearly grouped.

## Rules implemented

| Rule | Threshold | Level |
|---|---|---|
| PAN velocity 5 min | > 3 transactions | BLOCK |
| PAN velocity 1 hour | > 10 transactions | BLOCK |
| Impossible travel | pre-computed flag | BLOCK |
| Terminal velocity | > 200 txns/hour | HIGH |
| First PAN transaction | amount > ₹50,000 | HIGH |
| High-risk MCC | 5094, 5944, 7995 | HIGH |
| Round amount | multiple of ₹10,000, ≥ ₹10,000 | MEDIUM |

## Test coverage

- **31 tests** across `FraudEngineTest`
- Clean transaction → LOW, no triggered rules
- Each rule independently: fires at threshold+1, does NOT fire at threshold
- Boundary: PAN velocity exactly 3/10, terminal exactly 200, amount exactly ₹50k
- High-risk MCCs: all three (5094, 5944, 7995) individually verified
- Round amounts: ₹10k, ₹20k, ₹50k, ₹100k → MEDIUM; ₹6k, ₹9,999.99, ₹15,000.50 → no flag
- Round amount below ₹10k → no flag
- Multiple rules: BLOCK+MEDIUM → BLOCK; HIGH+MEDIUM → HIGH; all triggered rules collected
- `FraudVelocityData` negative counts → `IllegalArgumentException`
- Null context and null velocity → `NullPointerException`

## How to verify

```bash
mvn test -pl domain
# → 355 tests, 0 failures

mvn verify -pl domain
# → All coverage checks have been met (≥90%)
```
