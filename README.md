# NexSwitch

[![CI](https://github.com/sohom-poddar-11012002/PaymentsPlatform/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/sohom-poddar-11012002/PaymentsPlatform/actions/workflows/ci-cd.yml)
[![Coverage](https://img.shields.io/badge/domain%20coverage-%E2%89%A590%25-brightgreen)](#testing)
[![Java](https://img.shields.io/badge/Java-26-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

A production-grade Indian payment switch and acquirer processor built on hexagonal architecture. Implements the protocols and patterns used by payment infrastructure companies — ISO 8583 over persistent TCP, PKCS#11 cryptography, real-time fraud scoring, Spring Batch settlement, three-way reconciliation, and an adversarial QA orchestration platform.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Portals and Frontends](#portals-and-frontends)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Running the Stack](#running-the-stack)
- [Testing](#testing)
- [Services and Ports](#services-and-ports)
- [Configuration](#configuration)
- [CI/CD](#cicd)
- [Project Structure](#project-structure)
- [Roadmap](#roadmap)

---

## Overview

The platform processes card-present and UPI transactions end-to-end — from terminal connection through network routing, cryptographic verification, fraud evaluation, settlement, and reconciliation — with a dedicated adversarial QA layer for regression testing the whole stack.

**Core capabilities:**

| Capability | Implementation |
|---|---|
| Card-present transactions | ISO 8583 MTI 0100/0200/0400/0800 over persistent TCP (jPOS) |
| Contactless NFC | EMV contactless, CDCVM, CVM limit enforcement |
| Dynamic and static UPI QR | ZXing generation, Redis TTL sessions, SSE push to frontend |
| Timeout and reversal | 15s network timeout, MTI 0400, late-response race condition detection |
| HSM cryptography | ARQC verification, DUKPT PIN block translation, ARPC generation (SoftHSM2 / PKCS#11) |
| Fraud scoring | 7 rule-based rules (velocity, impossible travel, high-risk MCC) across BLOCK / HIGH / MEDIUM levels |
| Webhook delivery | HMAC-SHA256 signed, exponential backoff retry, Kafka dead-letter queue |
| Settlement | Spring Batch 5-step chunk job, daily at 23:30 IST, CSV per network to S3 |
| Reconciliation | Three-way match — switch log vs network file vs bank statement, 6 mismatch categories |
| Chargeback lifecycle | Evidence packaging, reserve account management |
| QA orchestration | Recorder-first adversarial test platform — scenarios, runs, suites, SSE live dashboard |
| Frontends | Four Next.js 15 App Router portals — simulator, merchant dashboard, ops dashboard, QA portal |

---

## Architecture

Hexagonal architecture (Ports and Adapters), enforced at build time by ArchUnit. CI fails on any architecture violation.

```
┌─────────────────────────────────────────────────────────────┐
│                        Domain Core                          │
│  Pure Java. Zero external dependencies.                     │
│  No Spring · No Kafka · No JPA · No HTTP.                   │
│                                                             │
│  TransactionStateMachine   FraudEngine    RoutingEngine     │
│  FeeWaterfallCalculator    LuhnValidator  QRSessionManager  │
└──────────────────────────┬──────────────────────────────────┘
                           │  imports domain only
┌──────────────────────────▼──────────────────────────────────┐
│                    Application Layer                        │
│  Spring Boot wiring only. No business logic.                │
│  @Bean · @Configuration · use-case orchestration            │
└──────────────────────────┬──────────────────────────────────┘
                           │  imports domain + application
┌──────────────────────────▼──────────────────────────────────┐
│                    Adapters Layer                           │
│  All infrastructure. Implements domain ports.               │
│  jPOS · SoftHSM2 · Kafka · PostgreSQL · Redis · S3          │
│  Domain never imports from here.                            │
└─────────────────────────────────────────────────────────────┘
```

**Enforcement rules checked in CI (ArchUnit):**
- Domain module has zero external dependencies
- No `@Autowired` field injection — constructor injection only
- Controllers depend only on use-case interfaces
- Domain services depend only on domain types
- QA domain is a fully isolated bounded context — zero imports from production domain

**Key domain patterns:**
- `Transaction` — aggregate root; static factory, guard-then-throw, in-memory domain events
- `TransactionStatus` — 20-state machine; `VALID_TRANSITIONS` map gives O(1) validation
- `AuthorizationResult`, `NetworkRoute`, `ReversalResult` — sealed interfaces; compiler enforces exhaustive handling
- `Money` — `BigDecimal` + `Currency` value object; `HALF_UP` scale-2 enforced at construction; never `double`

---

## Tech Stack

### Core

| Layer | Technology |
|---|---|
| Language | Java 26 (Temurin) |
| Framework | Spring Boot 4.0.6 |
| ISO 8583 | jPOS 2.1.9 |
| Cryptography | SoftHSM2 (PKCS#11) via SunPKCS11 |
| Database | PostgreSQL 16 — `NUMERIC(15,2)`, Flyway migrations |
| Cache | Redis 7 — idempotency keys, BIN lookup cache, QR sessions |
| Messaging | Apache Kafka — transactional outbox pattern, manual offset commit |
| Batch | Spring Batch 5 — 5-step chunk-oriented settlement job |
| ORM | Spring Data JPA + Hibernate (CRUD) · jOOQ (complex queries) |
| Mapper | MapStruct — compile-time code generation, zero reflection |
| QR codes | ZXing 3.5.3 |
| Build | Maven 3.9 multi-module (15 modules) |
| Frontend | Next.js 15 App Router · TypeScript strict · Tailwind v3 · shadcn/ui |
| E2E tests | Playwright |
| Architecture tests | ArchUnit 1.3.0 |
| Coverage gate | JaCoCo 0.8.14 — ≥90% line coverage enforced on domain module |
| Containers | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Cloud | AWS ECS Fargate · RDS · ElastiCache · S3 · CloudFront |
| Local infra | LocalStack (S3) · MailHog (SMTP) · Jaeger (traces) |

### v2.0 Roadmap Additions

| Category | Technologies |
|---|---|
| API patterns | gRPC · GraphQL + subscriptions · OpenAPI 3.1 |
| Resilience | Resilience4j (circuit breaker, bulkhead, rate limiter) |
| Workflow orchestration | Temporal — durable workflows replacing hand-rolled sagas |
| CDC | Debezium + Schema Registry + Avro |
| Search | Elasticsearch 8 |
| IaC | Terraform (full AWS stack) |
| Kubernetes | Helm · ArgoCD GitOps · Argo Rollouts · KEDA · HPA |
| Observability | Grafana LGTM stack (Loki + Tempo + Mimir + OTel Collector) |
| AI / ML fraud | LangGraph · pgvector HNSW · RAG pipeline · Claude Haiku · Langfuse |
| Security CI | Trivy · SonarQube · OWASP Dependency Check · Semgrep |
| Chaos and load | LitmusChaos · k6 (500 TPS target, p95 < 500ms) |
| Mobile | React Native + Expo · ProximityReader NFC · watchOS companion |
| ISO 20022 | pacs.008 / camt.053 — large-value settlement path |

---

## Portals and Frontends

The platform ships four Next.js 15 App Router portals. Each is independently deployable and talks to its corresponding backend service.

### Payment Simulator — `frontend/simulator` · port 3000

For developers and testers who want to fire transactions without a physical POS terminal.

| Feature | Detail |
|---|---|
| Card form | PAN / expiry / CVV input with Luhn validation feedback |
| Network detection | Real-time BIN lookup — Visa, Mastercard, RuPay, AMEX, Diners |
| QR mode | Generates dynamic UPI QR (ZXing), SSE-streams payment result back |
| ISO 8583 event log | Live stream of request/response fields (MTI, field 39, STAN, ARN) |
| Scenarios | One-click test scenarios: normal, contactless, timeout, duplicate |

---

### Merchant Dashboard — `frontend/dashboard` · port 3001

Operations visibility for a merchant receiving payments.

| Feature | Detail |
|---|---|
| Transaction feed | Real-time table of authorizations, reversals, refunds with status badges |
| Switch health | Circuit breaker states (HSM, upstream network, Kafka), Redis hit rate |
| Settlement status | Spring Batch job history, last run timestamp, settlement file download |
| Webhook log | Delivery attempts, HTTP status codes, HMAC signature verification result |
| Chargeback tracker | Open cases, deadlines, evidence upload status |

---

### Ops Dashboard — `frontend/ops` · port 3002

Platform operations and back-office control panel.

| Feature | Detail |
|---|---|
| Batch job control | Trigger, pause, restart Spring Batch settlement and reconciliation jobs |
| Dead-letter queue | Browse Kafka DLQ topics, inspect payloads, replay or discard messages |
| Reconciliation report | Three-way match results — matched, unmatched, missing, duplicate, amount mismatch |
| Audit log | Immutable audit trail of all transaction state transitions with MDC fields |
| Service registry | Health check matrix for all 9 backend services; latency p50/p95 |
| Config flags | Toggle adapter providers (upstream, HSM, storage) without redeployment |

---

### QA Portal — `frontend/qa-portal` · port 3003

Adversarial test platform for breaking the payment stack. The QA orchestrator runs the tests; the portal is the live control plane.

**Scenarios Browser — `/scenarios`**

Scenarios are organised as a tree: `Platform → Project → Feature → Scenario`. Drill down to `acquiring-service / payments / authorization` to see all Visa/RuPay/Mastercard happy-path scenarios, or `acquiring-service / payments / security` for replay-attack and oversized-field tests. Each card shows channel type, description, and YAML path.

**Run Trigger — `/runs`**

Select a pre-configured run (a chain of scenarios with shared or isolated context), review the `STATEFUL` / `STATELESS` session mode badge, and click **Trigger Run**. You land immediately on the live viewer for that execution.

**Live Viewer — `/live/[runId]`**

Server-Sent Events stream from the orchestrator. Steps appear in a `StepTimeline` as they execute — green tick for pass, red cross for fail, yellow pause for `WaitForHuman`. When the engine parks on a human step, a `WaitForHumanBanner` renders with the instruction text, expiry time, and **Continue** / **Mark as Failed** buttons. Clicking Continue resumes the virtual thread in the orchestrator.

**Reports — `/reports`**

Pass rate bar (green ≥80%, amber ≥50%, red below), slowest-five runs by elapsed time, most-failed scenarios ranked by failure count — useful for spotting flaky infrastructure scenarios.

**Scenario taxonomy (stored in YAML, not DB):**

```
scenarios/
└── acquiring-service/          ← platform
    └── payments/               ← project
        ├── authorization/      ← feature  (Visa, RuPay, Mastercard, reversal)
        ├── boundary/           ← feature  (invalid Luhn, zero amount, unknown BIN, …)
        ├── security/           ← feature  (replay attack, oversized field)
        ├── concurrency/        ← feature  (50 parallel auths same card)
        └── infrastructure/     ← feature  (circuit breaker open, Redis down, …)
```

Adding a new platform (e.g. `ops-portal / onboarding / login`) requires only a new YAML directory — no schema migration, no code change.

**Session modes:**

| Mode | Behaviour | Use case |
|---|---|---|
| `STATEFUL` | Captured variables (e.g. auth token, approval code) flow from scenario N to scenario N+1 | Login → action → logout flows |
| `STATELESS` | Each scenario starts from run-level baseline only | Adversarial boundary and security suites |

**Human-in-the-loop steps:**

`WaitForHuman` steps park the engine on a `CompletableFuture` (virtual thread — no OS thread blocked). The portal shows a banner with the operator instruction (e.g. "verify the receipt on the physical terminal"). The operator performs the real-world action, then clicks Continue. The future completes, the engine resumes, and the next step runs.

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (Temurin) | 26.0.1+ | Homebrew OpenJDK 25 will not work — use Temurin 26 |
| Maven | 3.9+ | Must run under Java 26 |
| Node.js | 20+ | For portal dev servers |
| Docker | 25+ | Required for local infrastructure |
| Docker Compose | v2 | Bundled with Docker Desktop |

**Java configuration:** If `mvn --version` shows Java 25 rather than Java 26, set `JAVA_HOME` explicitly:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-26.jdk/Contents/Home
```

---

## Getting Started

```bash
# 1. Clone the repository
git clone https://github.com/sohom-poddar-11012002/NexSwitch.git
cd NexSwitch

# 2. Verify Java 26
java -version        # must show 26.x
mvn --version        # Java version line must show 26.x

# 3. Build all modules (runs domain tests + JaCoCo gate)
mvn install --batch-mode

# 4. Start local infrastructure
docker compose up postgres redis kafka zookeeper mailhog localstack jaeger mock-upstream
```

**SoftHSM2 (optional — required for real ARQC / PIN block operations):**

```bash
# macOS
brew install softhsm
softhsm2-util --init-token --slot 0 --label "nexswitch-hsm" --pin 1234 --so-pin 5678

# .env
HSM_PROVIDER=softhsm
HSM_PKCS11_LIB=/opt/homebrew/lib/softhsm/libsofthsm2.so
```

Without SoftHSM2, `MockHsmAdapter` is used automatically (`HSM_PROVIDER=mock`).

---

## Running the Stack

**Full stack via Docker Compose:**

```bash
docker compose up

# Tail a specific service
docker compose logs -f acquiring-service
docker compose logs -f qa-orchestrator
```

**Individual services:**

```bash
# Start infrastructure first
docker compose up postgres redis kafka zookeeper mock-upstream

# Acquiring service
mvn spring-boot:run -pl services/acquiring-service

# QA orchestrator
mvn spring-boot:run -pl services/qa-orchestrator

# Any portal (dev server with hot reload)
cd frontend/qa-portal && npm install && npm run dev  # port 3003
cd frontend/simulator && npm install && npm run dev  # port 3000
```

**Terminal simulator scenarios:**

| Scenario | Description |
|---|---|
| `NORMAL_PURCHASE` | Standard chip + PIN, approval expected |
| `CONTACTLESS` | NFC, amount ≤ ₹5000, no PIN required |
| `CONTACTLESS_PIN` | NFC, amount > ₹5000, CVM limit exceeded |
| `TIMEOUT` | Network response suppressed — triggers 15s reversal |
| `DECLINE_NSF` | Field 39 = 51 (insufficient funds) |
| `DUPLICATE` | Same STAN twice — idempotency gate test |
| `PARTIAL_REVERSAL` | Authorize ₹10,000, capture ₹8,000 |

**QA orchestrator quick-start:**

```bash
# Trigger a run
curl -X POST http://localhost:8700/api/qa/runs/trigger \
  -H 'Content-Type: application/json' \
  -d '{"runId":"golden-path-run","variableOverrides":{}}'
# → {"executionId":"<uuid>"}

# Stream live events
curl -N http://localhost:8700/api/qa/runs/<uuid>/stream

# Or open the portal
open http://localhost:3003
```

---

## Testing

```bash
# Unit tests + ArchUnit (no infrastructure required)
mvn test

# Domain module only (also enforces JaCoCo 90% gate)
mvn test -pl domain

# QA orchestrator tests (33 tests: 9 arch + 8 assertion + 8 engine + 8 resolver)
mvn test -pl services/qa-orchestrator

# Integration tests with Testcontainers (real Postgres / Redis / Kafka)
mvn verify -P integration-tests

# Skip tests
mvn install -DskipTests
```

**Test pyramid:**

| Layer | Tool | Notes |
|---|---|---|
| Unit (domain) | JUnit 5 + AssertJ | Pure Java, no Spring context, ~1s |
| Architecture | ArchUnit | Hexagonal rules, constructor injection, QA domain isolation, ~2s |
| Adapter | `@DataJpaTest` + Testcontainers | Real Postgres + Redis, ~10s |
| Integration | `@SpringBootTest` + Testcontainers | Full service stack, ~30s |
| Adversarial (QA) | QA orchestrator — ISO 8583, REST, Kafka | Live stack end-to-end; human-in-the-loop steps |
| Chaos | WireMock delay / drop + Docker pause | Timeout, race condition, circuit breaker scenarios |
| Contract | Pact | Terminal ↔ acquiring ↔ switch |
| Load | k6 | 500 TPS, p95 < 500ms |

**JaCoCo gate:** ≥90% line coverage enforced on the domain module. Build fails below threshold.

---

## Services and Ports

### Backend

| Service | Port | Description |
|---|---|---|
| `acquiring-service` | 8080 (REST), 8000 (ISO 8583 TCP) | Entry point — ISO 8583 ingestion, idempotency, QR |
| `payment-switch` | 8100 | Routing engine, HSM crypto, fraud scoring, timeout/reversal |
| `mock-upstream` | 8001–8004 | Mock Visa / Mastercard / RuPay / UPI switch |
| `webhook-dispatcher` | — | Kafka consumer → HMAC-signed HTTP delivery |
| `merchant-simulator` | 9000 | Simulated merchant webhook receiver |
| `settlement-service` | — | Spring Batch settlement job (23:30 IST) |
| `reconciliation-service` | — | Three-way reconciliation (07:00 IST) |
| `notification-service` | — | Kafka → Thymeleaf email |
| `chargeback-service` | 8600 | Chargeback lifecycle and reserve account management |
| `qa-orchestrator` | 8700 (REST + SSE) | Adversarial test execution engine; scenario YAML loader |

### Frontends

| Portal | Port | Audience | Key Feature |
|---|---|---|---|
| Payment Simulator | 3000 | Dev / QA | Card form, QR mode, ISO 8583 event log |
| Merchant Dashboard | 3001 | Merchant ops | Transaction feed, webhook log, chargeback tracker |
| Ops Dashboard | 3002 | Platform ops | Batch job control, DLQ management, reconciliation report |
| QA Portal | 3003 | QA / Engineering | Scenario tree, run trigger, SSE live viewer, WaitForHuman banner, reports |

### Tools (CLI)

| Tool | Description |
|---|---|
| `terminal-simulator` | Standalone POS terminal emulator — fires ISO 8583 scenarios against acquiring-service |

### Infrastructure (Docker Compose)

| Service | Port | UI |
|---|---|---|
| PostgreSQL | 5432 | — |
| Redis | 6379 | — |
| Kafka | 9092 | — |
| MailHog | 1025 (SMTP) | http://localhost:8025 |
| LocalStack | 4566 | — |
| Jaeger | 4317 (OTLP) | http://localhost:16686 |

---

## Configuration

### Adapter toggles

All adapters swap via a single environment variable — no domain code changes required.

| Variable | Values | Default | Description |
|---|---|---|---|
| `UPSTREAM_PROVIDER` | `wiremock` · `razorpay` | `wiremock` | Network upstream |
| `HSM_PROVIDER` | `mock` · `softhsm` | `mock` | HSM adapter |
| `STORAGE_PROVIDER` | `local` · `s3` | `local` | File storage |

### Key environment variables

```env
# Database
DB_URL=jdbc:postgresql://localhost:5432/nexswitch
DB_USERNAME=nexswitch_app
DB_PASSWORD=local_dev_password

# Redis
REDIS_HOST=localhost
REDIS_PASSWORD=local_dev_password

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Adapters
UPSTREAM_PROVIDER=wiremock
HSM_PROVIDER=mock
STORAGE_PROVIDER=local

# HSM (when HSM_PROVIDER=softhsm)
HSM_PKCS11_LIB=/usr/lib/softhsm/libsofthsm2.so
HSM_TOKEN_PIN=1234

# QA Orchestrator
QA_ISO8583_HOST=localhost
QA_ISO8583_PORT=8000

# AWS (production — use IAM role on ECS, not static keys)
AWS_REGION=ap-south-1
S3_BUCKET=nexswitch-dev
```

### Spring profiles

| Profile | Use | Logging |
|---|---|---|
| `local` | Docker Compose | DEBUG |
| `dev` | AWS dev cluster | DEBUG |
| `qa` | AWS QA cluster | INFO |
| `prod` | AWS production | INFO |

---

## CI/CD

GitHub Actions — `.github/workflows/ci-cd.yml`

```
push feat/** or fix/**  →  test
push main               →  test → integration-test → build-push → deploy-dev → deploy-qa → deploy-prod (manual gate)
```

| Job | Trigger | Action |
|---|---|---|
| `test` | All branches | Unit tests + ArchUnit + JaCoCo |
| `integration-test` | `main` | Testcontainers integration suite |
| `build-push` | `main` | Docker build → ECR (tagged `{sha}` + `latest`) |
| `deploy-dev` | After build | ECS rolling update — dev cluster |
| `deploy-qa` | After deploy-dev | ECS rolling update — QA cluster |
| `deploy-prod` | Manual approval | ECS rolling update — production |

The same Docker image SHA is promoted through all environments — no rebuilds between stages.

---

## Project Structure

```
nexswitch/
├── pom.xml                          ← parent POM, 15 modules
├── docker-compose.yml               ← full local infrastructure stack
├── .github/
│   └── workflows/ci-cd.yml
│
├── domain/                          ← zero external dependencies (enforced by ArchUnit)
│   └── src/main/java/com/nexswitch/domain/
│       ├── model/                   ← Transaction, TransactionStatus, value objects
│       │   ├── vo/                  ← Money, PanHash, MerchantId, TerminalId, STAN, ARN
│       │   └── event/               ← domain events (TransactionAuthorizedEvent, …)
│       ├── port/
│       │   ├── inbound/             ← use-case interfaces (ProcessPaymentUseCase, …)
│       │   └── outbound/            ← port interfaces (AuthorizationPort, HsmPort, …)
│       └── service/                 ← TransactionStateMachine, FraudEngine, RoutingEngine, …
│
├── application/                     ← Spring @Configuration only, no business logic
├── adapters/                        ← all infrastructure (jPOS, SoftHSM2, JPA, Kafka, Redis)
├── test-support/                    ← Testcontainers singletons + shared test fixtures
│
├── services/
│   ├── acquiring-service/           ← ISO 8583 TCP + REST entry point (port 8000/8080)
│   ├── payment-switch/              ← routing, HSM crypto, fraud, reversal (port 8100)
│   ├── mock-upstream/               ← mock Visa / Mastercard / RuPay / UPI (ports 8001–8004)
│   ├── webhook-dispatcher/          ← HMAC-signed event delivery
│   ├── merchant-simulator/          ← webhook receiver (port 9000)
│   ├── settlement-service/          ← Spring Batch daily settlement job
│   ├── reconciliation-service/      ← three-way match (switch log + network file + bank)
│   ├── notification-service/        ← Kafka → Thymeleaf email
│   ├── chargeback-service/          ← chargeback lifecycle (port 8600)
│   └── qa-orchestrator/             ← adversarial test execution engine (port 8700)
│       └── src/main/resources/
│           ├── scenarios/           ← YAML test scenarios (platform/project/feature/*)
│           ├── runs/                ← YAML run definitions (ordered scenario chains)
│           └── suites/              ← YAML suite definitions (sets of runs)
│
├── frontend/
│   ├── simulator/                   ← Next.js 15 — card + QR payment simulator (port 3000)
│   ├── dashboard/                   ← Next.js 15 — merchant dashboard (port 3001)
│   ├── ops/                         ← Next.js 15 — ops + batch control dashboard (port 3002)
│   └── qa-portal/                   ← Next.js 15 — QA scenario browser + live run viewer (port 3003)
│       └── app/
│           ├── scenarios/           ← grouped by platform/project/feature
│           ├── runs/                ← trigger panel + execution history
│           ├── live/[runId]/        ← SSE StepTimeline + WaitForHumanBanner
│           └── reports/             ← pass rate, slowest runs, most-failed scenarios
│
├── tools/
│   └── terminal-simulator/          ← standalone POS terminal emulator (CLI)
│
└── docs/
    ├── code-learning-map.md         ← file → CS concept → one-line insight
    ├── project-walkthrough.md
    └── ticket-*.md                  ← design decisions per closed ticket
```

---

## Roadmap

### v1.0.0 — Core platform (in progress)

ISO 8583 golden path · SoftHSM2 cryptography · dynamic QR · timeout/reversal · webhooks · Spring Batch settlement · three-way reconciliation · chargeback lifecycle · four Next.js 15 portals · QA orchestration platform (phases 1–2 complete, phases 3–4 in progress) · AWS ECS deployment

**QA platform phases:**

| Phase | Status | Scope |
|---|---|---|
| Phase 1 | Done (PR #121) | Domain model, all adapters, SSE, REST API, 12 scenarios, 24 unit tests |
| Phase 2 | Done (PR #128) | Platform/Project/Feature taxonomy, SessionConfig, QA portal MVP |
| Phase 3 | Planned (#124) | KafkaAssertionAdapter, ChaosTestAdapter, 11 remaining adversarial scenarios, suite trigger, reports analytics |
| Phase 4 | Planned (#125) | ISO 8583 recorder proxy, HAR importer, Playwright adapter, scheduling |

### v2.0.0 — Platform extensions

| Area | Additions |
|---|---|
| APIs | gRPC service-to-service · GraphQL BFF · OpenAPI 3.1 |
| Resilience | Resilience4j circuit breaker and bulkhead · Temporal durable workflows |
| Data | Debezium CDC · Schema Registry + Avro · Elasticsearch |
| Infrastructure | Terraform · Kubernetes + Helm · ArgoCD · KEDA · Grafana LGTM |
| AI / ML | LangGraph multi-agent fraud scoring · pgvector HNSW · RAG · Langfuse evals |
| Security | Trivy · SonarQube · OWASP Dependency Check · Semgrep · LitmusChaos |
| Mobile | React Native NFC · watchOS companion · ISO 20022 large-value path |

### Simulated components

| Component | Simulator | Notes |
|---|---|---|
| Visa / Mastercard / NPCI / UPI networks | `mock-upstream` (jPOS, ports 8001–8004) | Implements full MTI 0110 / 0810 response flow |
| Hardware HSM (Thales Luna / Utimaco) | SoftHSM2 (PKCS#11) | Identical interface; swapped via `hsm.provider` config |
| ISO 20022 pacs.008 / camt.053 | Internal message builder | Format fully implemented; no live SWIFT/RTGS endpoint |
| Physical POS terminal | `terminal-simulator` (CLI JAR) | ISO 8583 builder, DUKPT key derivation, all EMV scenarios |

---

## License

[MIT](LICENSE)
