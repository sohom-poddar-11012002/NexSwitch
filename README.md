# Payments Platform

A production-grade distributed payments platform built in Java 21 / Spring Boot 3.4.x that replicates the core internal architecture of an Indian payment switch and acquirer processor — modelled on how companies like Juspay, Pine Labs, and Payswiff operate internally.

This is **not** a Razorpay/Stripe integration wrapper. It **is** the layer those companies build internally.

---

## Architecture

Hexagonal architecture (Ports & Adapters) enforced at build time by ArchUnit. The domain core has zero external dependencies — no Spring, no Kafka, no JPA.

```
┌─────────────────────────────────────────────┐
│              Domain Core                    │
│  Pure Java 21. Zero external dependencies.  │
│  Models, Ports (interfaces), Services.      │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│           Application Layer                 │
│  Spring Boot wiring only. No business logic.│
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│            Adapters Layer                   │
│  jPOS, SoftHSM2, Kafka, PostgreSQL,         │
│  Redis, S3. Implements domain ports.        │
└─────────────────────────────────────────────┘
```

---

## Services

| Service | Port(s) | Description |
|---|---|---|
| acquiring-service | 8000 (ISO 8583 TCP), 8080 (REST) | Entry point for all terminal and QR transactions |
| payment-switch | 8100 | Core routing, HSM operations, fraud engine |
| mock-upstream | 8001–8004, 8090 | Mock Visa / Mastercard / RuPay / UPI networks |
| webhook-dispatcher | 8200 | Reliable event delivery with HMAC-SHA256 signing |
| merchant-simulator | 9000 | Simulates a merchant backend receiving webhooks |
| settlement-service | 8300 | Spring Batch settlement job, daily at 23:30 IST |
| reconciliation-service | 8400 | Three-way reconciliation: switch vs network vs bank |
| notification-service | 8500 | Kafka → email dispatcher via Thymeleaf templates |
| chargeback-service | 8600 | Full chargeback lifecycle management |
| terminal-simulator | — | Standalone POS terminal emulator (no Spring Boot) |

**Frontends** (React 18 + Vite + Tailwind):

| App | Port | Description |
|---|---|---|
| Payment Simulator | 3000 | Card form + QR mode + live ISO 8583 event log |
| Merchant Dashboard | 3001 | Live transaction feed, switch health, settlement status |
| Ops Dashboard | 3002 | Batch job control, dead letter queue, alerts |

---

## Tech Stack

**Backend**
- Java 21, Spring Boot 3.4.x
- jPOS 2.1.9 — ISO 8583 over persistent TCP
- SoftHSM2 (PKCS#11) — ARQC verification, PIN block translation, ARPC generation
- PostgreSQL 16 — transactional data (NUMERIC(15,2) for all money, never float/double)
- Redis 7 — idempotency, BIN cache (L1/L2/L3), QR sessions, correlation store
- Apache Kafka — event-driven state transitions, transactional outbox pattern
- Spring Batch 5 — settlement job with 5-step chunk processing
- Flyway — database migrations (V1–V11), owned exclusively by acquiring-service
- MapStruct — compile-time bidirectional domain ↔ JPA entity mapping
- ZXing — dynamic UPI QR code generation
- ArchUnit — architecture rules enforced at build time in CI

**Infrastructure**
- Docker Compose — full local stack
- AWS ECS Fargate — production deployment
- AWS RDS PostgreSQL, ElastiCache Redis
- LocalStack — local S3 simulation
- Jaeger — distributed tracing via OpenTelemetry
- MailHog — local email capture

**Frontend**
- React 18, Vite, Tailwind CSS
- React Query, Axios, Recharts
- SSE (Server-Sent Events) for real-time transaction feeds

---

## Key Features

**ISO 8583 over persistent TCP**
Full MTI coverage: 0100/0110 (auth), 0200/0210 (capture), 0400/0410 (reversal), 0800/0810 (network management). Persistent connections with heartbeat, exponential backoff reconnect, and primary/secondary failover per network.

**Hardware Security Module (SoftHSM2)**
ARQC verification, PIN block translation (ISO 9564 Format 0), ARPC generation, MAC verification on every inbound message. MockHsmAdapter available for local development.

**Transaction State Machine**
20 states with enforced valid transitions. Every transition logged to audit_log (append-only at DB level). Invalid transitions throw `InvalidStateTransitionException`.

**Timeout + Reversal with Race Condition Handling**
15-second timeout monitor fires MTI 0400 reversal. Late 0110 arriving after reversal is detected and discarded — transaction ends in REVERSED, never incorrectly AUTHORIZED.

**Dynamic UPI QR**
ZXing-generated QR with Redis TTL session. SSE push on payment confirmation. Auto-refund on expired session with late credit.

**Webhook Dispatch**
HMAC-SHA256 signed payloads. Manual Kafka offset commit. 3-retry exponential backoff (30s → 2min → 10min). Dead letter topic after 3 failures.

**Spring Batch Settlement**
5-step job: aggregate → generate CSV per network → validate totals → submit to network → notify. Restart from last checkpoint on failure.

**Three-Way Reconciliation**
Switch log vs network settlement file vs bank MT940 statement. Six mismatch categories. UNKNOWN transaction resolution. Fee waterfall calculation (interchange → network assessment → MDR → reserve withholding → payout).

---

## Maven Module Structure

```
payments-platform/           ← parent POM, 14 modules
├── domain/                  ← zero external dependencies
├── application/             ← shared Spring @Configuration only
├── adapters/                ← all infrastructure implementations
├── test-support/            ← singleton Testcontainers + fixtures (test scope)
├── services/
│   ├── acquiring-service/
│   ├── payment-switch/
│   ├── mock-upstream/
│   ├── webhook-dispatcher/
│   ├── merchant-simulator/
│   ├── settlement-service/
│   ├── reconciliation-service/
│   ├── notification-service/
│   └── chargeback-service/
├── frontend/
│   ├── simulator/
│   ├── dashboard/
│   └── ops/
└── tools/
    └── terminal-simulator/  ← standalone Java app, no Spring Boot
```

---

## Local Development

**Prerequisites:** Java 21, Maven 3.9+, Docker

```bash
# Copy env template
cp .env.example .env

# Start all infrastructure + services
docker compose up

# Or start infrastructure only and run services from IDE
docker compose up postgres redis kafka zookeeper mock-upstream

# Build all modules
mvn install

# Run terminal simulator
java -jar tools/terminal-simulator/target/terminal-simulator-*.jar
```

**Local endpoints:**

| Endpoint | URL |
|---|---|
| Payment Simulator UI | http://localhost:3000 |
| Merchant Dashboard | http://localhost:3001 |
| Ops Dashboard | http://localhost:3002 |
| Acquiring REST API | http://localhost:8080 |
| Acquiring ISO 8583 TCP | http://localhost:8000 |
| Payment Switch | http://localhost:8100 |
| MailHog (email UI) | http://localhost:8025 |
| Jaeger (traces UI) | http://localhost:16686 |
| LocalStack (S3) | http://localhost:4566 |

---

## Testing

```bash
# Unit + ArchUnit tests (all modules)
mvn test

# Integration tests with Testcontainers (main branch only in CI)
mvn verify -P integration-tests

# Domain module only — enforces 90% line coverage
mvn test -pl domain
```

Test pyramid: unit tests (domain, pure Java, no Spring) → adapter tests (@DataJpaTest with Testcontainers) → integration tests (@SpringBootTest) → chaos tests (WireMock delay/drop scenarios).

---

## CI/CD

GitHub Actions pipeline:

1. **Build & Test** — all pushes to `main`, `feat/**`, `fix/**`
2. **Integration Tests** — Testcontainers, main branch only
3. **Build & Push** — Docker images to Amazon ECR, main branch only
4. **Deploy** — ECS Fargate rolling update, main branch only

---

## Configuration

Key environment variables (see `.env.example`):

| Variable | Values | Description |
|---|---|---|
| `UPSTREAM_PROVIDER` | `wiremock` / `razorpay` | Network upstream adapter |
| `HSM_PROVIDER` | `mock` / `softhsm` | HSM adapter selection |
| `STORAGE_PROVIDER` | `local` / `s3` | File storage adapter |

All adapters are swappable via a single config property — no domain code changes required.
