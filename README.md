# Payments Platform

A production-grade distributed payments platform built in Java 26 / Spring Boot 4.0.x that replicates the core internal architecture of an Indian payment switch and acquirer processor — modelled on how companies like Juspay, Pine Labs, and Payswiff operate internally.

**This is not a Razorpay/Stripe integration wrapper.** It is the layer those companies build internally — ISO 8583 over persistent TCP, SoftHSM2 cryptography, real state machines, real reconciliation, real fraud rules.

---

## Table of Contents

- [What This Is](#what-this-is)
- [Architecture](#architecture)
- [Implementation Status](#implementation-status)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Local Setup](#local-setup)
- [Running the Project](#running-the-project)
- [Testing](#testing)
- [CI/CD Pipeline](#cicd-pipeline)
- [Configuration Reference](#configuration-reference)
- [Services and Ports](#services-and-ports)
- [Roadmap](#roadmap)
- [Project Structure](#project-structure)

---

## What This Is

A payment switch and acquirer processor that handles:

- **Card present transactions** — ISO 8583 MTI 0100/0200/0400/0800 over persistent TCP
- **Contactless NFC** — EMV contactless, CDCVM, tap-to-pay flows
- **Dynamic and static UPI QR** — ZXing QR generation, Redis TTL sessions, SSE push
- **Timeout + reversal with race condition handling** — 15s timeout, MTI 0400, late 0110 discard
- **Cryptography** — ARQC verification, PIN block translation, ARPC generation via SoftHSM2 (PKCS#11)
- **Fraud scoring** — 7 rule-based rules across BLOCK/HIGH/MEDIUM levels; ML scoring port ready for post-June
- **Webhook dispatch** — HMAC-SHA256 signed, exponential backoff retry, dead letter queue
- **Spring Batch settlement** — 5-step job, daily at 23:30 IST, S3 CSV per network
- **Three-way reconciliation** — switch log vs network file vs bank statement, 6 mismatch categories
- **Chargeback lifecycle** — evidence packaging, reserve account management
- **Three Next.js 15 frontends** — simulator, merchant dashboard, ops dashboard

Target architecture narrative for interviews:

> "I built a payment switch from scratch — ISO 8583 over persistent TCP, SoftHSM2 for ARQC verification and PIN block translation, dynamic QR with Redis TTL sessions, timeout and reversal handling with race condition detection, webhook dispatch with HMAC-SHA256 signing, Spring Batch settlement with three-way reconciliation, three Next.js 15 App Router frontends — deployed on AWS ECS Fargate behind CloudFront."

---

## Architecture

Hexagonal architecture (Ports & Adapters), enforced at build time by ArchUnit. CI fails on any architecture violation.

```
┌─────────────────────────────────────────────────────┐
│                    Domain Core                      │
│  Pure Java 26. Zero external dependencies.          │
│  No Spring. No Kafka. No JPA. No HTTP.              │
│  Models · Ports (interfaces) · Services             │
│                                                     │
│  TransactionStateMachine  FraudEngine               │
│  RoutingEngine            FeeWaterfallCalculator     │
│  LuhnValidator            QRSessionManager          │
└──────────────────────┬──────────────────────────────┘
                       │ depends on ↑ only
┌──────────────────────▼──────────────────────────────┐
│                 Application Layer                   │
│  Spring Boot wiring only. No business logic.        │
│  @Bean · @Configuration · use-case orchestration    │
└──────────────────────┬──────────────────────────────┘
                       │ depends on ↑ only
┌──────────────────────▼──────────────────────────────┐
│                  Adapters Layer                     │
│  All infrastructure. Implements domain ports.       │
│  jPOS · SoftHSM2 · Kafka · PostgreSQL · Redis · S3 │
│  Domain never imports from here.                    │
└─────────────────────────────────────────────────────┘
```

**Enforcement rules (ArchUnit, checked in CI):**
- Domain has zero external dependencies
- No `@Autowired` field injection anywhere — constructor injection only
- Controllers only depend on use-case interfaces
- Domain services only use domain types

---

## Implementation Status

### v1.0.0 Sprint (May–June 2026)

| # | Ticket | Status | PR |
|---|--------|--------|----|
| #5 | Domain value objects (Money, PanHash, MerchantId, …) | ✅ Done | #19 |
| #6 | Domain models (Transaction, TransactionStatus, enums) | ✅ Done | #21 |
| #7 | All ports — inbound use cases, outbound ports, sealed results | ✅ Done | #22 |
| #8 | TransactionStateMachine — 20 states, all valid transitions | ✅ Done | #23 |
| #9 | FraudEngine — 7 rules, BLOCK/HIGH/MEDIUM, ML score port ready | ✅ Done | #24 |
| #10 | LuhnValidator, RoutingEngine, FeeWaterfallCalculator | 🔲 Next | — |
| #11 | Domain events | 🔲 Planned | — |
| #12 | acquiring-service — ISO 8583 TCP + validation chain | 🔲 Planned | — |
| #13 | payment-switch — BIN lookup, HSM, timeout/reversal | 🔲 Planned | — |
| #14 | Dynamic QR + Redis TTL session | 🔲 Planned | — |
| #15 | Webhook dispatcher — HMAC, retry, DLQ | 🔲 Planned | — |
| #16 | Settlement + reconciliation | 🔲 Planned | — |
| #17 | Chargeback service | 🔲 Planned | — |
| #18 | Next.js 15 frontends (simulator, dashboard, ops) | 🔲 Planned | — |

**Test counts:** 355 domain unit tests passing · JaCoCo ≥ 90% enforced on domain module

---

## Tech Stack

### Current (v1.0.0 sprint)

| Layer | Technology |
|---|---|
| Language | Java 26 (Temurin) |
| Framework | Spring Boot 4.0.6 |
| ISO 8583 | jPOS 2.1.9 |
| Cryptography | SoftHSM2 (PKCS#11) via SunPKCS11 |
| Database | PostgreSQL 16 — NUMERIC(15,2), never float/double |
| Cache | Redis 7 — idempotency, BIN cache, QR sessions |
| Messaging | Apache Kafka — transactional outbox, manual offset commit |
| Batch | Spring Batch 5 — settlement (5-step chunk) |
| Migrations | Flyway — sequential V1–V11, immutable |
| ORM | Spring Data JPA + Hibernate (CRUD) · jOOQ (complex queries) |
| Mapper | MapStruct — compile-time, zero reflection |
| QR codes | ZXing 3.5.3 |
| Architecture tests | ArchUnit 1.3.0 |
| Coverage gate | JaCoCo 0.8.12 — 90% line coverage on domain |
| Build | Maven 3.9 multi-module (14 modules) |
| Frontend | Next.js 15 App Router · TypeScript strict · Tailwind v4 · shadcn/ui |
| E2E tests | Playwright |
| Containers | Docker + Docker Compose |
| CI/CD | GitHub Actions (test → integration → build → deploy) |
| Cloud | AWS ECS Fargate · RDS · ElastiCache · S3 · CloudFront |
| Local infra | LocalStack (S3) · MailHog (email) · Jaeger (traces) |

### Post-June additions (v2.0.0 roadmap)

| Category | Technologies |
|---|---|
| API patterns | gRPC · GraphQL + subscriptions · OpenAPI |
| Resilience | Resilience4j (circuit breaker, bulkhead, rate limiter) |
| Orchestration | Temporal (durable workflows — replaces hand-rolled saga) |
| CDC | Debezium + Schema Registry + Avro (replaces outbox scheduler) |
| Search | Elasticsearch 8 + Kibana |
| API gateway | Kong |
| Connection pooling | PgBouncer |
| IaC | Terraform (full AWS stack) |
| Kubernetes | kind cluster · Helm charts · NetworkPolicy · HPA · PDB |
| GitOps | ArgoCD · Argo Rollouts (canary + blue-green) |
| Autoscaling | KEDA (Kafka lag, Redis queue depth, scale to zero) |
| Observability | Grafana LGTM stack (Loki + Tempo + Mimir + OTel Collector) |
| AI/ML | LangGraph · pgvector (HNSW) · RAG · Langfuse · Claude Haiku |
| Feature flags | OpenFeature SDK + Unleash |
| Security pipeline | Trivy · SonarQube · OWASP Dep Check · Semgrep · detect-secrets |
| Chaos | LitmusChaos · k6 load tests (500 TPS target) |
| Mobile | React Native + Expo · ProximityReader NFC (iOS) · watchOS companion |
| ISO 20022 | pacs.008 / camt.053 — large-value settlement path |

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (Temurin) | 26.0.1+ | Required — Homebrew OpenJDK 25 will NOT work |
| Maven | 3.9+ | Must run under Java 26 — see note below |
| Docker | 25+ | For local infrastructure stack |
| Docker Compose | v2 | Bundled with Docker Desktop |

> **Important — JAVA_HOME**: Maven defaults to whatever JDK it was installed with. If `mvn --version` shows Java 25 (Homebrew), Maven will fail with `release version 26 not supported`. Fix permanently:
>
> ```bash
> echo 'export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-26.jdk/Contents/Home' >> ~/.zshrc
> source ~/.zshrc
> ```
>
> Or prefix individual Maven commands:
> ```bash
> JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-26.jdk/Contents/Home mvn install
> ```

---

## Local Setup

```bash
# 1. Clone
git clone https://github.com/sohom-poddar-11012002/PaymentsPlatform.git
cd PaymentsPlatform

# 2. Verify Java 26 is active
java -version        # must show 26.x
mvn --version        # Java version line must show 26.x

# 3. Install all modules (runs domain tests + JaCoCo gate)
mvn install --batch-mode

# 4. Copy environment template
cp .env.example .env
# Edit .env if you want non-default passwords

# 5. Start local infrastructure
docker compose up postgres redis kafka zookeeper mailhog localstack jaeger mock-upstream
```

SoftHSM2 (optional — for real ARQC operations):
```bash
# macOS
brew install softhsm
softhsm2-util --init-token --slot 0 --label "payments-hsm" --pin 1234 --so-pin 5678

# Set in .env:
# HSM_PROVIDER=softhsm
# HSM_PKCS11_LIB=/opt/homebrew/lib/softhsm/libsofthsm2.so
```

Without SoftHSM2, the MockHsmAdapter is used automatically (`HSM_PROVIDER=mock` default).

---

## Running the Project

### Full stack via Docker Compose

```bash
# All services + infrastructure
docker compose up

# Watch logs for a specific service
docker compose logs -f acquiring-service
```

### Individual services from terminal / IDE

```bash
# Start infra first
docker compose up postgres redis kafka zookeeper mock-upstream

# Each service from its module directory (Spring Boot devtools auto-restart enabled)
mvn spring-boot:run -pl services/acquiring-service

# Terminal simulator (standalone Java app, no Spring)
java -jar tools/terminal-simulator/target/terminal-simulator-*.jar \
  --scenario NORMAL_PURCHASE
```

**Available terminal simulator scenarios:**
```
NORMAL_PURCHASE     standard chip+PIN
CONTACTLESS         NFC, amount ≤ ₹5000, no PIN
CONTACTLESS_PIN     NFC, amount > ₹5000, PIN required
TIMEOUT             no response — triggers 15s reversal
DECLINE_NSF         Field 39 = 51
DUPLICATE           same STAN twice — idempotency test
PARTIAL_REVERSAL    auth ₹10000, capture ₹8000
```

### Local endpoints

| Endpoint | URL |
|---|---|
| Payment Simulator UI | http://localhost:3000 |
| Merchant Dashboard | http://localhost:3001 |
| Ops Dashboard | http://localhost:3002 |
| Acquiring REST API | http://localhost:8080 |
| Acquiring ISO 8583 TCP | localhost:8000 |
| Payment Switch | http://localhost:8100 |
| Jaeger (traces) | http://localhost:16686 |
| MailHog (email) | http://localhost:8025 |
| LocalStack (S3) | http://localhost:4566 |

---

## Testing

```bash
# Unit tests + ArchUnit (all modules) — fast, no infrastructure
mvn test

# Domain module only — also enforces JaCoCo 90% gate
mvn test -pl domain

# Integration tests with Testcontainers (real Postgres/Redis/Kafka)
mvn verify -P integration-tests

# Skip tests for a fast build
mvn install -DskipTests
```

**Test pyramid:**

| Layer | Tool | Speed | What |
|---|---|---|---|
| Unit (domain) | JUnit 5 + AssertJ | ~1s | Pure Java, no Spring context |
| Architecture | ArchUnit | ~2s | Hexagonal + SOLID rules enforced |
| Adapter | @DataJpaTest + Testcontainers | ~10s | Real Postgres/Redis |
| Integration | @SpringBootTest + Testcontainers | ~30s | Full service stack |
| Chaos | WireMock delay/drop | ~60s | Timeout, race conditions, duplicates |
| Contract | Pact | ~20s | Terminal ↔ acquiring ↔ switch |
| Load | k6 | ~10min | 500 TPS, p95 < 500ms — post-June |

**Coverage gate:** JaCoCo enforces 90% line coverage on the domain module. Build fails below threshold.

**Test tags:**
```bash
# Run only unit tests
mvn test -Dgroups=unit

# Integration tests only
mvn test -Dgroups=integration

# Exclude chaos and load (default CI behaviour)
mvn test -DexcludedGroups=chaos,load
```

---

## CI/CD Pipeline

GitHub Actions — `.github/workflows/ci-cd.yml`

```
push feat/** or fix/**  →  [test]
push main               →  [test] → [integration-test] → [build-push] → [deploy-dev] → [deploy-qa] → [deploy-prod ⏸️]
```

| Job | Trigger | What |
|---|---|---|
| `test` | All branches | Unit + ArchUnit · JaCoCo report |
| `integration-test` | `main` only | Testcontainers (real infra) |
| `build-push` | `main` + `DEPLOY_ENABLED=true` | Docker build → ECR (tagged `{sha}` + `latest`) |
| `deploy-dev` | After build-push | ECS rolling update on `payments-platform-dev` |
| `deploy-qa` | After deploy-dev stable | ECS rolling update on `payments-platform-qa` |
| `deploy-prod` | After deploy-qa + **manual approval** | ECS rolling update on `payments-platform` |

AWS stages are gated by `vars.DEPLOY_ENABLED` (not a secret — secrets can't be used in `if:` conditions). Set to `'true'` in July when AWS is provisioned.

**Same Docker image SHA is promoted through all environments** — no rebuilds, truly immutable artifact.

---

## Configuration Reference

### Adapter toggles

| Variable | Values | Default | Description |
|---|---|---|---|
| `UPSTREAM_PROVIDER` | `wiremock` / `razorpay` | `wiremock` | Network upstream |
| `HSM_PROVIDER` | `mock` / `softhsm` | `mock` | HSM adapter |
| `STORAGE_PROVIDER` | `local` / `s3` | `local` | File storage |
| `DB_PROVIDER` | `postgres` | `postgres` | Database adapter |

All adapters swap via a single config property — zero domain code changes.

### Key environment variables

```env
# Database
DB_URL=jdbc:postgresql://localhost:5432/payments
DB_USERNAME=payments_app
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

# Email
SPRING_MAIL_HOST=localhost
SPRING_MAIL_PORT=1025

# AWS (production only — use IAM role on ECS, not keys)
AWS_REGION=ap-south-1
S3_BUCKET=payments-platform-dev
```

### Spring profiles

| Profile | Use | Logging | show-sql |
|---|---|---|---|
| `local` | Docker Compose | DEBUG | true |
| `dev` | AWS ECS dev cluster | DEBUG | true |
| `qa` | AWS ECS qa cluster | INFO | false |
| `prod` | AWS ECS production | INFO | false |

---

## Services and Ports

### Backend services

| Service | Ports | Description |
|---|---|---|
| `acquiring-service` | 8000 (ISO 8583 TCP), 8080 (REST) | Entry point — ISO 8583 validation, idempotency, QR |
| `payment-switch` | 8100 | Routing, HSM crypto, fraud engine, timeout/reversal |
| `mock-upstream` | 8001 (Visa), 8002 (MC), 8003 (RuPay), 8004 (UPI) | Mock Visa/MC/NPCI switch |
| `webhook-dispatcher` | — | Kafka consumer → HMAC-signed HTTP delivery |
| `merchant-simulator` | 9000 | Simulated merchant backend receiving webhooks |
| `settlement-service` | — | Spring Batch job at 23:30 IST |
| `reconciliation-service` | — | Three-way match at 07:00 IST |
| `notification-service` | — | Kafka → Thymeleaf email templates |
| `chargeback-service` | 8600 | Chargeback lifecycle, evidence packaging, reserve accounts |
| `terminal-simulator` | — | Standalone POS terminal emulator |

### Frontend apps

| App | Port | Description |
|---|---|---|
| Payment Simulator | 3000 | Card form + QR mode + live ISO 8583 event log |
| Merchant Dashboard | 3001 | Transaction feed, switch health, settlement status, QR |
| Ops Dashboard | 3002 | Batch job control, dead letter queue, switch health |

### Infrastructure (Docker Compose)

| Service | Port | UI |
|---|---|---|
| PostgreSQL | 5432 | — |
| Redis | 6379 | — |
| Kafka | 9092 | — |
| MailHog | 1025 (SMTP) | http://localhost:8025 |
| LocalStack (S3) | 4566 | — |
| Jaeger | 4317 (OTLP) | http://localhost:16686 |
| mock-upstream | 8001–8004 | — |

---

## Roadmap

### v1.0.0 — Full backend + all three frontends (June 2026)

Complete ISO 8583 golden path, SoftHSM2 cryptography, dynamic QR, timeout/reversal, webhooks, Spring Batch settlement, three-way reconciliation, chargeback lifecycle, all three Next.js 15 App Router frontends, AWS ECS deployment, CloudFront CDN.

### v1.1.0–v1.6.0 — Post-June additions (July–September 2026, weekends)

Progressive enhancements built over 13 weekend sessions:

| Version | Target | What |
|---|---|---|
| v1.1 | Jul W1-W2 | TypeScript strict frontend migration · OpenAPI · Sentry · PgBouncer · Circuit Breaker · Feature flags · OWASP ZAP |
| v1.2 | Jul W3-W4 | LangGraph multi-agent fraud scoring · pgvector HNSW · Streaming embedding pipeline · Langfuse · Evals (ragas + promptfoo) · Guardrails AI · Prompt caching · LiteLLM |
| v1.3 | Aug W1-W2 | Terraform IaC (full AWS stack) |
| v1.4 | Aug W3-W4 | Kubernetes + Helm · ArgoCD GitOps · Argo Rollouts canary/blue-green · KEDA · Grafana LGTM stack |
| v1.5 | Sep W1-W2 | gRPC · GraphQL · Temporal workflows · Debezium CDC · Schema Registry + Avro · ISO 20022 pacs.008/camt.053 |
| v1.6 | Sep W3-W4 | Security CI pipeline (Trivy, SonarQube, Semgrep) · LitmusChaos · k6 load tests · Storybook · PostHog |
| v2.0 | Sep 30 | MLflow + DVC + fine-tuning (LoRA/vLLM) · MCP server · NFC Tap to Pay (iOS/Android) · Apple Watch companion |

### Stubbed — documented, not yet implemented

- Real NPCI/Visa/MC network connections (legally impossible as an individual — WireMock mock)
- React Native merchant app with NFC Tap to Pay (§20.8–20.9 in CLAUDE.md)
- Apple Watch companion (§20.10)
- ISO 20022 large-value settlement path
- ML fraud scoring service (port is defined — `FraudScoringPort`, `FraudScore.mlRiskScore()`)
- Grafana dashboards (Prometheus metrics are exposed, dashboards are July addition)

---

## Project Structure

```
payments-platform/
├── pom.xml                          ← parent POM, 14 modules, dependency management
├── CLAUDE.md                        ← complete project spec and architecture reference
├── docker-compose.yml               ← full local stack
├── .github/
│   └── workflows/ci-cd.yml          ← 6-job pipeline (test → build → deploy-dev → qa → prod)
│
├── domain/                          ← ZERO external dependencies, enforced by ArchUnit
│   └── src/main/java/com/payments/domain/
│       ├── model/                   ← Transaction, TransactionStatus, enums, FraudVelocityData
│       │   └── vo/                  ← Money, PanHash, MerchantId, TerminalId, STAN, ARN, AuthCode
│       ├── port/
│       │   ├── inbound/             ← use-case interfaces (ProcessPaymentUseCase, …)
│       │   └── outbound/            ← port interfaces (AuthorizationPort, HsmPort, …)
│       └── service/                 ← TransactionStateMachine, FraudEngine, RoutingEngine, …
│
├── application/                     ← Spring @Configuration only, no business logic
├── adapters/                        ← all infrastructure (jPOS, SoftHSM2, Postgres, Redis, Kafka)
├── test-support/                    ← shared Testcontainers singletons + test fixtures
│
├── services/
│   ├── acquiring-service/           ← ISO 8583 TCP + REST entry point
│   ├── payment-switch/              ← core routing and processing
│   ├── mock-upstream/               ← mock Visa/MC/NPCI/UPI
│   ├── webhook-dispatcher/          ← HMAC-signed event delivery
│   ├── merchant-simulator/          ← webhook receiver for testing
│   ├── settlement-service/          ← Spring Batch daily job
│   ├── reconciliation-service/      ← three-way match algorithm
│   ├── notification-service/        ← Kafka → email
│   └── chargeback-service/          ← chargeback lifecycle
│
├── frontend/
│   ├── simulator/                   ← Next.js 15 — card + QR simulator
│   ├── dashboard/                   ← Next.js 15 — merchant dashboard
│   └── ops/                         ← Next.js 15 — ops dashboard
│
├── tools/
│   └── terminal-simulator/          ← standalone Java POS terminal emulator
│
└── docs/
    ├── learning-concepts-checklist.md
    ├── project-walkthrough.md
    └── ticket-*.md                  ← one per closed ticket
```

---

## Demo Script

**Setup:** `docker compose up` → wait ~60 seconds → `http://localhost:3000`

**Demo 1 — Card transaction (45s)**
Open Simulator → Card mode → PAN `4539148803436467`, Expiry `12/28`, CVV `123`, Amount `₹6000`, Scenario `Normal Purchase` → Pay. Watch event log: ARQC verified, fraud LOW, AUTHORIZED in ~800ms. Switch to Dashboard → transaction appears in live feed. Click row → full ISO 8583 detail drawer.

**Demo 2 — Dynamic QR (45s)**
Simulator → QR mode → `₹500` → Generate. Scannable QR appears. Scan with phone → UPI app opens, amount pre-filled. Approve → Dashboard shows AUTHORIZED instantly via SSE. MailHog shows merchant webhook email.

**Demo 3 — Timeout + reversal (60s)**
Card mode → Scenario `Timeout + Reversal` → Pay. Watch: 0100 sent → 15s → reversal fired → REVERSED. Dashboard shows REVERSED with full state machine timeline.

**Demo 4 — Settlement + reconciliation (45s)**
Ops Dashboard → click `[Run Now]` on Settlement → Spring Batch job runs, progress visible → CSVs appear in LocalStack S3. Click `[Run Now]` on Reconciliation → three-way match runs → RECON summary. MailHog: ops completion email.
