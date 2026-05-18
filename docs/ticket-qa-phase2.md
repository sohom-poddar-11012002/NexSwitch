# Ticket QA Phase 2 — Taxonomy, Session Model, Portal MVP

## What
Upgraded the QA orchestration platform with a proper Platform→Project→Feature taxonomy, configurable session model for runs, and the full Next.js 15 portal.

## Why
Phase 1 used a flat `category` string which doesn't scale to multiple services/frontends. The platform needs to support `acquiring-service/payments/authorization` as well as `ops-portal/onboarding/login` without schema changes. Session config makes multi-scenario runs meaningful — a login→action→logout flow needs STATEFUL; adversarial boundary tests should be STATELESS so failures don't contaminate neighbours.

## Design Decisions

| Decision | Rationale |
|---|---|
| `platform/project/feature` replaces `category` | Scales to N services; portal groups/filters naturally at every level |
| `SessionConfig` on `TestRun`, not `TestScenario` | Session mode is a run-level concern — a scenario is agnostic to whether it's in a stateful chain |
| `carryVariables` allowlist (empty = carry all) | Opt-in precision: can pin just `auth_token` from login without leaking all 20 context vars |
| `isolateOnFailure` flag | Prevents broken captured state (e.g. null token) from poisoning all downstream assertions |
| `OnFailure` on `TestSuite` | Suite-level policy: boundary suites use CONTINUE; golden-path suite uses FAIL_FAST |
| Portal rewrite-proxy through `next.config.ts` | Portal proxies `/api/qa/*` to the orchestrator URL — no CORS config needed in Spring |

## What Changed

### Domain Layer
- `TestScenario`: replaced `category` with `platform`, `project`, `feature`
- `TestRun`: added `SessionConfig(SessionMode, carryVariables, isolateOnFailure)`
- `TestSuite`: added `OnFailure` enum and `schedule` cron field

### Repository + YAML
- `ScenarioRepository`: replaced `findScenariosByCategory` with `findScenariosByPlatform` + `findScenariosByFeature`
- `YamlScenarioRepository`: parses new taxonomy fields, `session` block in runs, `on_failure` in suites
- 12 scenario YAMLs reorganised: `scenarios/{platform}/{project}/{feature}/*.yml`

### Application Layer
- `TriggerRunService`: honours `SessionConfig` — STATELESS resets context per scenario, STATEFUL carries with optional allowlist

### REST API
- `GET /api/qa/scenarios?platform=X&project=Y&feature=Z` replaces `?category=X`

### Portal (`frontend/qa-portal`, port 3003)
- `/scenarios` — server component, grouped by platform/project/feature tree
- `/runs` — trigger panel (select run, see session mode badge), execution history table
- `/live/[runId]` — SSE-driven `StepTimeline` + `WaitForHumanBanner` (Continue / Mark Failed buttons)
- `/reports` — pass rate stat, slowest runs, most-failed scenarios

### Infrastructure
- `docker-compose.yml`: added `qa-orchestrator` (port 8700) and `frontend-qa-portal` (port 3003) services

## Test Coverage
- 33 tests, 0 failures (9 arch + 8 assertion + 8 engine + 8 resolver)
- Domain model changes caught by existing engine tests (record constructor arity check)

## How to Verify
```bash
# Backend
mvn clean test -pl services/qa-orchestrator

# Portal (local)
cd frontend/qa-portal && npm install && npm run dev
# Open http://localhost:3003/scenarios — grouped cards by platform/project/feature
# Click Runs → Trigger golden-path-run → redirected to /live/{id}
# Steps animate in real time; WaitForHuman banner appears on verify-receipt step
```
