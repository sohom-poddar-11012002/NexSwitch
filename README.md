# NexSwitch

[![CI](https://github.com/sohom-poddar-11012002/PaymentsPlatform/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/sohom-poddar-11012002/PaymentsPlatform/actions/workflows/ci-cd.yml)
[![Coverage](https://img.shields.io/badge/domain%20coverage-%E2%89%A590%25-brightgreen)](#testing)
[![Java](https://img.shields.io/badge/Java-26-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

A production-grade Indian payment switch and acquirer processor built on hexagonal architecture. Implements the protocols and patterns used by payment infrastructure companies — ISO 8583 over persistent TCP, PKCS#11 cryptography, real-time fraud scoring, Spring Batch settlement, and three-way reconciliation.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
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

The platform processes card-present and UPI transactions end-to-end, from terminal connection through network routing, cryptographic verification, fraud evaluation, and settlement.

**Core capabilities:**

| Capability | Implementation |
|---|---|
| Card-present transactions | ISO 8583 MTI 0100/0200/0400/0800 over persistent TCP (jPOS) |
| Contactless NFC | EMV contactless, CDCVM, CVM limit enforcement |
| Dynamic and static UPI QR | ZXing generation, Redis TTL sessions, SSE push to frontend |
| Timeout and reversal | 15s network timeout, MTI 0400, late-response race condition detection |
| HSM cryptography | ARQC verification, DUKPT PIN block translation, ARPC generation (SoftHSM2 / PKCS#11) |
| Fraud scoring | 7 rule-based rules (velocity, impossible travel, high-risk MCC) across BLOCK / HIGH / MEDIUM levels; ML scoring port defined |
| Webhook delivery | HMAC-SHA256 signed, exponential backoff retry, Kafka dead-letter queue |
| Settlement | Spring Batch 5-step chunk job, daily at 23:30 IST, CSV per network to S3 |
| Reconciliation | Three-way match — switch log vs network file vs bank statement, 6 mismatch categories |
| Chargeback lifecycle | Evidence packaging, reserve account management |
| Frontends | Three Next.js 15 App Router applications — terminal simulator, merchant dashboard, ops dashboard |

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
| Build | Maven 3.9 multi-module (14 modules) |
| Frontend | Next.js 15 App Router · TypeScript strict · Tailwind v4 · shadcn/ui |
| E2E tests | Playwright |
| Architecture tests | ArchUnit 1.3.0 |
| Coverage gate | JaCoCo 0.8.13 — ≥90% line coverage enforced on domain module |
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

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (Temurin) | 26.0.1+ | Homebrew OpenJDK 25 will not work — use Temurin 26 |
| Maven | 3.9+ | Must run under Java 26 |
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
git clone https://github.com/sohom-poddar-11012002/PaymentsPlatform.git
cd PaymentsPlatform

# 2. Verify Java 26
java -version        # must show 26.x
mvn --version        # Java version line must show 26.x

# 3. Build all modules (runs domain tests + JaCoCo gate)
mvn install --batch-mode

# 4. Copy environment template
cp .env.example .env

# 5. Start local infrastructure
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
```

**Individual services (from IDE or terminal):**

```bash
# Start infrastructure first
docker compose up postgres redis kafka zookeeper mock-upstream

# Start a service with Spring Boot devtools auto-restart
mvn spring-boot:run -pl services/acquiring-service

# Run the terminal simulator (standalone Java app)
java -jar tools/terminal-simulator/target/terminal-simulator-*.jar \
  --scenario NORMAL_PURCHASE
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

---

## Testing

```bash
# Unit tests + ArchUnit (no infrastructure required)
mvn test

# Domain module only (also enforces JaCoCo 90% gate)
mvn test -pl domain

# Integration tests with Testcontainers (real Postgres / Redis / Kafka)
mvn verify -P integration-tests

# Skip tests
mvn install -DskipTests
```

**Test pyramid:**

| Layer | Tool | Notes |
|---|---|---|
| Unit (domain) | JUnit 5 + AssertJ | Pure Java, no Spring context, ~1s |
| Architecture | ArchUnit | Hexagonal rules, constructor injection, ~2s |
| Adapter | `@DataJpaTest` + Testcontainers | Real Postgres + Redis, ~10s |
| Integration | `@SpringBootTest` + Testcontainers | Full service stack, ~30s |
| Chaos | WireMock delay / drop | Timeout, race condition, duplicate scenarios |
| Contract | Pact | Terminal ↔ acquiring ↔ switch |
| Load | k6 | 500 TPS, p95 < 500ms |

**JaCoCo gate:** ≥90% line coverage enforced on the domain module. Build fails below threshold.

**Test tags:**

```bash
mvn test -Dgroups=unit
mvn test -Dgroups=integration
mvn test -DexcludedGroups=chaos,load
```

---

## Services and Ports

### Backend

| Service | Ports | Description |
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
| `terminal-simulator` | — | Standalone POS terminal emulator (CLI) |

### Frontend

| App | Port | Description |
|---|---|---|
| Payment Simulator | 3000 | Card form + QR mode + live ISO 8583 event log |
| Merchant Dashboard | 3001 | Transaction feed, switch health, settlement status |
| Ops Dashboard | 3002 | Batch job control, dead-letter queue management |

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
├── pom.xml                          ← parent POM, 14 modules
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
│   ├── acquiring-service/           ← ISO 8583 TCP + REST entry point
│   ├── payment-switch/              ← routing, HSM crypto, fraud, reversal
│   ├── mock-upstream/               ← mock Visa / Mastercard / RuPay / UPI
│   ├── webhook-dispatcher/          ← HMAC-signed event delivery
│   ├── merchant-simulator/          ← webhook receiver
│   ├── settlement-service/          ← Spring Batch daily job
│   ├── reconciliation-service/      ← three-way match
│   ├── notification-service/        ← Kafka → email
│   └── chargeback-service/          ← chargeback lifecycle
│
├── frontend/
│   ├── simulator/                   ← Next.js 15 — card + QR simulator
│   ├── dashboard/                   ← Next.js 15 — merchant dashboard
│   └── ops/                         ← Next.js 15 — ops dashboard
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

ISO 8583 golden path · SoftHSM2 cryptography · dynamic QR · timeout/reversal · webhooks · Spring Batch settlement · three-way reconciliation · chargeback lifecycle · three Next.js 15 frontends · AWS ECS deployment

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

### Intentionally mocked — permanent by design

These are not gaps to be closed later. They are replaced by high-fidelity simulators because connecting to the real thing requires institutional licensing that no individual or portfolio project can obtain.

| What | Why it can't be real |
|---|---|
| Visa / Mastercard / NPCI live network | Requires Visa/MC **principal membership** — only licensed acquiring banks qualify. Involves $100K+ setup fees, formal PCI DSS Level 1 certification audits, and a network sponsor bank. The `mock-upstream` service (jPOS, ports 8001–8004) is the permanent substitute. |
| ISO 20022 pacs.008 / camt.053 submission to real banks | Requires a **SWIFT BIC** (assigned exclusively to financial institutions) or RBI RTGS authorisation. The message format itself is fully implemented; it just cannot be fired at a real bank network. |
| SoftHSM2 in place of a real HSM | A production HSM (Thales Luna, Utimaco) costs ~₹50L and requires a data-centre rack. SoftHSM2 implements the identical PKCS#11 interface — the domain code is indistinguishable. |

This is standard industry practice: Juspay, Pine Labs, and Payswiff all use simulators in their dev/QA environments for the same reason.

### Planned for later milestones (will be built)

| Feature | Milestone |
|---|---|
| ML fraud scoring (LangGraph + pgvector + Claude Haiku) | Week 8 |
| Grafana LGTM dashboards (Prometheus metrics already exposed via Actuator) | v2.0.0 |
| React Native NFC + Apple Watch companion | Week 11 |
| gRPC, GraphQL BFF, Debezium CDC, Elasticsearch | v2.0.0 |

---

## License

[MIT](LICENSE)
