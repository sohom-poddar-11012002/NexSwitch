# Ticket: QA Orchestration Platform — Phase 1

**PR:** #121  
**Branch:** feat/qa-orchestrator  
**Files:** 57 new files, 3038 insertions  
**Tests:** 24 unit tests green (VariableResolver × 8, AssertionEvaluator × 8, ScenarioExecutionEngine × 8)

---

## What

Built the complete Phase 1 foundation of an internal adversarial QA orchestration platform. The platform allows testing all NexSwitch surfaces (terminal, web, API, mobile) through a configurable scenario-driven engine — without touching production code and without mocking anything.

---

## Why

Approaching production deployment with no QA infrastructure beyond unit tests. The platform needs to test things that unit tests can never cover: real ISO 8583 wire protocol, physical terminal interactions, browser UI flows, race conditions, circuit breaker behaviour under real load. A dev playing QA needs to think "how do I break this" — this platform is the tooling for that.

---

## Architecture

```
Suite → Run → Scenario → Step
```

- **Suite**: set of runs, SEQUENTIAL or PARALLEL, schedulable
- **Run**: chained scenarios, shared session context, variables carry forward
- **Scenario**: single feature test, classified by platform / project / feature / category
- **Step**: sealed — `Send | Assert | WaitForHuman | Loop | InjectVariable`

Full hexagonal architecture — QA domain has zero imports from `com.nexswitch.domain.*`. The QA platform is entirely independent of production internals.

---

## Domain Services

### VariableResolver
Template substitution on `{{key}}` patterns. Built-ins:
- `{{$stan}}` — atomic counter, zero-padded 6 digits, unique per call
- `{{$uuid}}` — random UUID
- `{{$now}}` — epoch milliseconds
- `{{$loop_index}}` — current loop iteration
- `{{pan}}` — user-defined, looked up from execution context

### AssertionEvaluator
Two evaluation modes:
- **SpEL** — `field39 == '00'`, `amount > 0`, `status != 'DECLINED'`
- **JSONPath** — `$.auth_response.field39 == '00'` — reads from a JSON string stored in context

Key fix: `StandardEvaluationContext` requires `ctx.addPropertyAccessor(new MapAccessor())` to resolve map keys via dot-notation. Without it, SpEL throws `EL1008E: Property or field 'x' cannot be found on Map` — `ReflectivePropertyAccessor` does not handle Map key access.

### ScenarioExecutionEngine
Pure Java domain service — zero Spring annotations. Dispatches via sealed switch on `TestStep`. Key behaviours:
- **WaitForHuman**: registers a `CompletableFuture<ResumeOutcome>` in a `ConcurrentHashMap` keyed by `executionId:stepId`. Parks the virtual thread via `future.get(timeout, MILLISECONDS)`. Unparked by `POST /resume`.
- **Parallel Loop**: `CompletableFuture.supplyAsync(..., runnable -> Thread.ofVirtual().start(runnable))` — spawns N virtual threads, one per iteration.
- **fail_fast**: if an Assert step fails with `failFast=true`, all remaining steps are recorded as `Skipped`.
- **Variable propagation**: captured variables from each step's `Passed.captured()` map are merged back into the shared run context for downstream steps.

---

## Channel Adapters

### Iso8583TestAdapter
Reuses the same length-prefixed TCP framing as `terminal-simulator`'s `SwitchTcpClient`. Per-test TCP connection (no pooling — test throughput is low and isolation is more important). Maps YAML payload keys to ISO 8583 field numbers via a switch. Loads `cfg/terminal-packager.xml` from classpath.

### RestTestAdapter
Spring `RestClient` (Spring 6.1+). Parses operation string as `METHOD /path`. Base URL resolved from `rest_base_url` context variable (injected via trigger-time `variableOverrides`).

---

## Infrastructure

### YamlScenarioRepository
`PathMatchingResourcePatternResolver` scans `classpath*:scenarios/**/*.yml`. Parses YAML trees via SnakeYAML into domain records. Hot-reloadable via `POST /api/qa/reload`.

### Flyway isolation
`flyway.table: flyway_schema_history_qa` — completely separate migration history from acquiring-service's `flyway_schema_history`. Both on the same Postgres instance, no collision.

### SseEventPublisher
One `CopyOnWriteArrayList<SseEmitter>` per `executionId`. Multiple browser tabs can subscribe to the same run. Stale emitters (IOException on send) are removed inline. On run complete, all emitters for that `executionId` are completed and the list is removed.

---

## Design Decisions

| Decision | Rationale |
|---|---|
| Scenarios in YAML git files, not DB | Source of truth version-controlled; diff-able; reviewable in PRs |
| Virtual threads for WaitForHuman | 50 parallel runs with human pauses = near-zero OS thread cost |
| `MapAccessor` for SpEL | `ReflectivePropertyAccessor` cannot access Map keys by property name — required explicit registration |
| JSONPath strips root context key | `$.auth_response.field39` → reads `$.field39` from the value stored at `auth_response` in context |
| Per-test TCP connection in Iso8583TestAdapter | Clean isolation between test runs; no shared socket state |
| QA domain zero imports from production domain | Test infrastructure must never couple to production internals |
| `flyway_schema_history_qa` isolation | QA service can be deployed/upgraded independently of acquiring-service |

---

## Test Coverage

| Test class | Count | What it covers |
|---|---|---|
| `VariableResolverTest` | 8 | Built-ins ($stan counter, $uuid format, $now range), user vars, unknown var passthrough, null template, resolveAll map |
| `AssertionEvaluatorTest` | 8 | SpEL pass/fail, SpEL numeric, JSONPath pass/fail, missing context key, empty expression |
| `ScenarioExecutionEngineTest` | 8 | Golden path, assert failure, fail_fast skip, inject variable, missing adapter, serial loop, WaitForHuman resume, WaitForHuman timeout |

---

## Pre-built Scenario Library (12 of 23 planned)

```
terminal/acquiring-service/auth/
  visa-auth-approved, rupay-auth-approved, mastercard-auth-approved, reversal-after-auth

terminal/acquiring-service/boundary/
  invalid-luhn, zero-amount, unknown-bin, wrong-currency, max-amount

terminal/acquiring-service/security/
  replay-attack (field39=94), oversized-field

terminal/acquiring-service/infrastructure/
  circuit-breaker-open (field39=91 with CB force-open)
```

---

## How to Verify

```bash
# 1. Build
mvn compile -pl services/qa-orchestrator -am

# 2. Unit tests
mvn test -pl services/qa-orchestrator

# 3. Start acquiring-service, then qa-orchestrator
docker-compose up acquiring-service
mvn spring-boot:run -pl services/qa-orchestrator

# 4. Trigger golden path run
curl -X POST http://localhost:8700/api/qa/runs/trigger \
  -H 'Content-Type: application/json' \
  -d '{"runId":"golden-path-run"}'

# 5. Stream live events
curl -N http://localhost:8700/api/qa/runs/{executionId}/stream

# 6. Resume a WaitForHuman step
curl -X POST http://localhost:8700/api/qa/runs/{executionId}/steps/verify-receipt/resume \
  -d '{"outcome":"PASS"}'

# 7. Trigger security run — replay STAN must get field39=94
curl -X POST http://localhost:8700/api/qa/runs/trigger \
  -d '{"runId":"security-run"}'
```

---

## What's Next (Phase 2)

- Platform / Project / Feature classification on `TestScenario` (small model addition)
- Session config (STATEFUL / STATELESS, `carry_variables`) on `TestRun`
- `OnFailure` + cron scheduling on `TestSuite`
- Next.js 15 portal — `/scenarios`, `/runs`, `/live/[id]`, `/reports`, `/recorder`
- docker-compose wiring for qa-orchestrator + qa-portal
- Remaining 11 scenarios (concurrency, remaining security, infrastructure)
