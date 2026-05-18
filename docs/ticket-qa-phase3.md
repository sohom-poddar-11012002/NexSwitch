# Ticket QA Phase 3 — Adversarial Channels, Suite Execution, Reports Analytics

## What
Added `KafkaAssertionAdapter` and `ChaosTestAdapter` channel adapters, 11 remaining adversarial
scenario YAMLs, `TriggerSuiteUseCase` + `TriggerSuiteService` for suite-level orchestration,
`POST /api/qa/suites/trigger` endpoint, suite SSE events, and upgraded the `/reports` portal
page with a Recharts pass-rate trend line chart and p50/p95 step latency table.

## Why
Phase 2 left Kafka event assertions and infrastructure chaos tests unimplemented — the platform
couldn't verify that events fired after auth, or that services degraded gracefully under failures.
Suite execution was also missing: there was no way to run all three runs (golden-path, boundary,
security) in a single triggerable unit before deployment.

## Design Decisions

| Decision | Rationale |
|---|---|
| `KafkaConsumer` factory method `createConsumer()` | Protected override point so tests inject a mock without a real broker — identical to production adapter behaviour |
| `ChaosTestAdapter.CommandRunner` functional interface | `docker pause` runs as a subprocess; tests inject a lambda that captures the command args — fully testable, no Docker daemon required |
| Poll-to-completion in `TriggerSuiteService` | Simpler than a shared CompletableFuture registry; virtual threads make 500ms sleep loops zero-cost |
| `OnFailure.CONTINUE` doesn't set `suitePassed = false` | Suite is still considered failed if any run fails unless CONTINUE explicitly allows it — only CONTINUE mode ignores failure count |
| Recharts `PassTrendChart` as `"use client"` component | Recharts requires DOM access; server component passes pre-computed `TrendPoint[]` array, keeping data fetch server-side |

## Test Coverage
- 48 tests, 0 failures
- `KafkaAssertionAdapterTest` (4): event found, timeout, missing key, supports check
- `ChaosTestAdapterTest` (6): pause, unpause, sleep, unknown op, missing key, supports check
- `TriggerSuiteServiceTest` (5): sequential pass, fail-fast, continue, parallel, not found
- All prior tests (33) still green

## How to Verify
```bash
# Build + test
mvn test -pl services/qa-orchestrator

# Trigger suite via REST
curl -X POST http://localhost:8700/api/qa/suites/trigger \
  -H 'Content-Type: application/json' \
  -d '{"suiteId":"v1-full-suite"}'

# List suites
curl http://localhost:8700/api/qa/suites

# Portal — /reports shows pass-rate trend chart + p50/p95 table
```
