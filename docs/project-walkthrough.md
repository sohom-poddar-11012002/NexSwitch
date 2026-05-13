# Payments Platform — Complete Project Walkthrough

> A narrative reference covering the entire system: what it is, how every layer works,
> why every decision was made, and what gets built post-June. Written for interview prep,
> onboarding, and as a mental model document.

---

## What This System Actually Is

This is not a Razorpay integration wrapper. It is the layer that Razorpay, Juspay, and
Payswiff build internally. When a merchant swipes a card, there is an entire processing
chain the cardholder never sees: the terminal encrypts PIN and card data using a key
hierarchy managed by a hardware security module, builds a binary ISO 8583 message, sends
it over a persistent TCP connection to an acquirer's switch, which routes it to the card
network, which reaches the issuer, gets a response, and the whole chain reverses in under
a second. This system replicates that chain from the terminal simulator through the
acquiring service, payment switch, fraud engine, HSM operations, timeout and reversal
handling, settlement batch processing, three-way reconciliation, and webhook dispatch to
merchants — deployed on AWS ECS Fargate with three React frontends.

---

## Pre-June System: What Gets Built May 11 – June 30

### The Foundation: Domain Layer

The system is built on hexagonal architecture. The domain module has zero external
dependencies — no Spring, no Kafka, no JPA, no HTTP. It contains only pure Java 21:
immutable records for models, enums for state, sealed interfaces for result types, and
plain Java interfaces for ports.

Every monetary value is a `Money` record wrapping BigDecimal with a Currency. Double and
float are absolutely forbidden anywhere in the system. Every identifier is a typed value
object: `MerchantId`, `TerminalId`, `PanHash`, `AuthorizationCode`, `AcquirerReferenceNumber`,
`SystemTraceAuditNumber`. These self-validate on construction. `PanHash` stores SHA-256 of
the card number — the raw PAN never leaves the terminal.

The transaction state machine has 20 states and enforces every transition at the domain
level. Invalid transitions throw `InvalidStateTransitionException` immediately. The state
machine is the authoritative source of truth for what can happen to a transaction. You
cannot set a status field directly anywhere in the system — every state change passes
through the machine.

`TransactionStatus` enum holds all 20 states from `INITIATED` through `CHARGEBACK_LOST`.
The transition map is hard-coded in `TransactionStateMachine` — each state maps to a set
of states it can move to. Anything outside that map throws immediately.

The fraud engine runs rules inline: PAN velocity over Redis sliding windows, terminal
velocity, first transaction on new PAN above ₹50,000, round amounts, impossible travel
(same PAN at geographically impossible distance). Risk levels are `LOW / MEDIUM / HIGH /
BLOCK`. `BLOCK` means decline locally without forwarding to upstream. `HIGH` means forward
with a fraud flag in ISO 8583 Field 44. The `FraudScore` record carries an
`Optional<BigDecimal> mlRiskScore` field from day one — it is always empty during the
sprint but the architecture slot for post-June ML scoring is already in place.

### How a Card Transaction Actually Flows

A card is inserted into the terminal simulator. EMV chip cryptography runs: the card
generates an ARQC (Application Request Cryptogram) using a card session key derived from
the Issuer Master Key, the PAN, and the ATC (Application Transaction Counter). PIN is
entered, encrypted into an ISO 9564 Format 0 PIN block under the terminal's Zone PIN Key.
The terminal builds an ISO 8583 MTI 0100 Authorization Request with all required fields —
PAN in Field 2 (but we store only the hash), processing code in Field 3, amount in Field 4
(12 digits implied 2 decimal places), STAN in Field 11, EMV data including the ARQC in
Field 55, encrypted PIN block in Field 52.

The message arrives at the acquiring service over a persistent TCP connection on port 8000.
The binary message is parsed by jPOS. Field-by-field validation runs: mandatory fields
present, Luhn check on PAN, MAC verification via the HSM (SoftHSM2 PKCS#11). Idempotency
check: a Redis key `{stan}:{terminalId}:{date}` with 24-hour TTL catches duplicate
submissions from the terminal. Terminal and merchant validation: are they active, is the
terminal not suspended, is KYC valid? Per-transaction and daily rolling volume limits are
checked against Redis counters. If all passes, a `Transaction` domain object is created in
`INITIATED` state, written to Postgres with an audit log entry, and a Kafka event
`transaction.initiated` is published via the transactional outbox pattern.

The payment switch picks up the transaction. BIN lookup (first 6-8 digits of PAN) routes
the transaction: Visa, Mastercard, or NPCI RuPay. The BIN table is cached in a three-tier
hierarchy: Caffeine JVM cache (microsecond hit on top BINs), Redis shared cache
(millisecond), Postgres (fallback with cache stampede protection via Redis SETNX lock).
The switch runs ARQC verification via SoftHSM2: derive the card session key from the
Issuer Master Key + PAN + ATC, recompute the expected ARQC, compare. Failed ARQC means
a counterfeit card — decline immediately without forwarding. PIN block is translated from
the terminal ZPK to the network ZPK — the PIN block bytes change but the PIN never appears
in plaintext. An ARPC (Authorization Response Cryptogram) is generated to prove the
response is genuine. Fraud engine runs. State transitions to `AUTHORIZATION_PENDING`. A
correlation entry is written to Redis with the ARN+STAN key and 30-second TTL.

The 0100 message is forwarded over a persistent TCP connection to the mock network (jPOS
mock switch). A response 0110 comes back with Field 39 response code: `00` = approved,
`51` = insufficient funds, `91` = issuer down, etc. The switch matches the response via
ARN+STAN from the correlation store. Field 39 = `00`: verify ARPC, state transitions to
`AUTHORIZED`, Kafka event published, webhook dispatched to the merchant's endpoint via
HMAC-SHA256 signed POST. The 0110 response is sent back to the terminal. Receipt prints.

### Timeout and Reversal

The timeout monitor runs every 100 milliseconds scanning the correlation store for entries
older than 15 seconds. When one is found, an MTI 0400 Reversal Request is built with
Field 90 (original data elements) and sent upstream. State transitions to
`REVERSAL_PENDING`. A Kafka event `transaction.reversal_initiated` is published.

The mock network responds with 0410. Field 39 = `00`: reversal accepted, state moves to
`REVERSED`. Field 39 = `25`: original not found, which also means `REVERSED` (the issuer
already knows there was no authorization). If the reversal itself times out, state becomes
`UNKNOWN`.

The race condition: an 0110 arrives after the reversal was already sent. The switch checks
whether a reversal is in flight. If yes, the 0110 is discarded — `AUTHORIZED` never
happens after `REVERSAL_PENDING`. The system waits for the 0410, then moves to `REVERSED`.
This is explicitly tested in chaos tests with a 16-second delayed response scenario.

### Dynamic QR Payment

A cashier calls `POST /qr/generate` with amount and merchant ID. The acquiring service
generates a transaction reference in format `TXN{yyyyMMddHHmmss}{merchantId}{sequence}`,
stores a Redis session with 5-minute TTL, builds the UPI deep link string
`upi://pay?pa=merchant@payswiff&am=6000.00&tr={txnRef}&cu=INR`, encodes it into a QR
image using ZXing at error correction level M, and returns the base64 PNG with the
transaction reference and expiry time.

The customer scans, the UPI app opens with amount pre-filled, the customer approves with
UPI PIN. NPCI sends a credit notification to the acquiring service:
`POST /upi/credit { npciTxnId, payerVPA, amount, txnRef }`. The service looks up the Redis
session by txnRef, verifies the amount matches, creates a transaction record in
`AUTHORIZED` state, deletes the QR session, and publishes Kafka events. The merchant's
POS polls `GET /qr/status/{txnRef}` every 2 seconds or receives an SSE push — either way
it learns the payment is complete immediately.

If the QR expires (Redis TTL fires) and a late payment arrives, the session lookup fails.
The credit goes into an exception queue, an auto-refund is initiated, and an ops alert
fires. This edge case is explicitly handled.

### Settlement

Spring Batch job runs daily at 23:30 IST. Step 1 reads all `CAPTURED` transactions via
paginated JPA query, validates and formats them, calculates fees. Step 2 generates one
CSV settlement file per network (Visa, Mastercard, NPCI) and uploads to S3 (LocalStack
locally). Step 3 validates totals balance, checks for duplicates, signs the file with
SoftHSM2. Step 4 submits the files to the mock network endpoints and records network
batch IDs. Step 5 publishes a Kafka settlement event and sends an ops email with stats.

The fee waterfall per transaction: gross amount minus interchange fee to the issuing bank,
minus network assessment fee to Visa/MC/NPCI, minus Payswiff MDR equals net to merchant,
minus 5% reserve withholding equals actual payout via NEFT.

### Three-Way Reconciliation

Reconciliation matches three data sources: the Postgres `SETTLEMENT_PENDING` transactions
(what Payswiff believes happened), the network settlement file from S3 (what the card
network believes happened), and the mock MT940 bank statement from S3 (what the bank
believes was settled). Each transaction must match by ARN in all three. Mismatch categories:
missing in network (settlement not processed), missing in switch (critical — the network
has a transaction Payswiff has no record of), missing in bank statement, amount mismatch,
duplicate in network file, or UNKNOWN transaction found in network file (which lets the
switch resolve a previously timed-out transaction to `AUTHORIZED` or `REVERSED`).

### Webhooks

The webhook dispatcher consumes `transaction.events` from Kafka with manual offset commit.
For each event: look up the merchant's webhook URL and secret, build JSON payload, sign
with HMAC-SHA256, add `X-Payswiff-Signature: sha256={hex}` header and a delivery UUID,
POST to merchant endpoint. 2xx: commit offset, record delivered. Non-2xx or timeout:
retry at 30 seconds, 2 minutes, 10 minutes. After 3 failures: dead letter topic, record
failed, ops alert. The merchant simulator receives and validates the HMAC, can be
configured to return 500 for a percentage of requests to test retry logic.

### Chargeback Lifecycle

The chargeback service receives a chargeback from the mock network, creates a record,
finds the original transaction, debits the merchant reserve by the transaction amount plus
₹350 fee, parks in a suspense account, sets a response deadline (Visa: 30 days, Mastercard:
45 days), and emails both merchant and ops. The merchant can contest by uploading evidence.
The evidence package is a ZIP file in S3 containing the original ISO 8583 0100 message
bytes, ARQC verification result, PIN verification result, EMV chip confirmation, ARPC
confirmation, terminal audit log, and the authorization code. States flow:
`RECEIVED → CONTESTED → EVIDENCE_SUBMITTED → WON or LOST`.

---

## Infrastructure and Operations

### Every Request Is Auditable

Every state transition writes to both `transaction_events` (for domain queries) and
`audit_log` (append-only, `REVOKE UPDATE, DELETE` at the database level). The audit log
survives log rotation and service restarts. MDC context propagation ensures that every
log line for a request carries the trace ID, transaction ID, merchant ID, and card last 4
as structured fields, so Kibana or Loki can filter the complete history of any transaction
across all services in one query.

### Redis Key Patterns

The Redis key design tells you how the system thinks: idempotency keys live 24 hours,
QR sessions 5 minutes, correlation entries 30 seconds (double the timeout window), BIN
cache 24 hours (it barely changes), fraud velocity keys use sliding window TTLs matched
to the window size (5-minute velocity key has 5-minute TTL that resets on each write),
merchant daily volume is keyed by date and expires at end of business day.

### Kafka Topology

12 partitions on `transaction.events` means 12 parallel consumer instances. Producers
use `acks: all` and idempotent mode — no duplicate events, no lost events. Consumers
use manual offset commit with `isolation-level: read_committed` — processing failure
leaves the event in the topic for retry, never auto-commits and loses it.

### Health and Observability

Every service exposes liveness (is the JVM alive?) and readiness (can it serve traffic?)
separately. Custom health indicators report HSM status, network switch primary/secondary
status per card network with last heartbeat timestamp and latency, Kafka producer error
rate. Prometheus metrics cover authorization latency at p50/p95/p99 per network, TPS,
fraud block rate, cache hit/miss rates, switch connection status as a gauge, webhook
delivery success rate. OpenTelemetry auto-instrumentation spans every request including
ISO 8583 round trips. Logs are structured JSON in production, human-readable in development,
with MDC fields embedded in every line.

---

## Post-June Enhancements: What Gets Added July – September

### The Strategy

From July, the project is maintained on weekends only while weekdays go to DSA and system
design preparation. The goal by September 30 is v2.0.0 — a deliberately overengineered
system that covers every technology topic that comes up at tier-1 Indian product and
fintech companies.

### Backend Additions

**gRPC** replaces REST between payment-switch and fraud-scoring-service. Protocol Buffers
give binary serialization 3-10x smaller than JSON. Server-streaming RPC allows the fraud
service to stream intermediate scoring results (velocity check done, vector search done,
LLM reasoning in progress) back to the switch in real time.

**GraphQL API** adds a new `graphql-api` module using Spring for GraphQL. The dashboard
and ops frontends switch to GraphQL queries for flexible field selection — get exactly the
fields you need per view, no over-fetching. GraphQL subscriptions over WebSocket replace
the SSE transaction feed with a more structured real-time channel. DataLoader solves N+1
at the resolver level, batching merchant lookups into single queries.

**Resilience4j** wraps every upstream call — ISO 8583 adapters, HSM, webhook delivery, ML
fraud scoring. The circuit breaker pattern: too many failures in a window opens the
circuit, subsequent calls fail fast instead of waiting for timeout. Bulkhead: separate
thread pools per card network mean a Visa network outage cannot exhaust threads that handle
Mastercard traffic. All state exposed to Prometheus — `resilience4j_circuitbreaker_state`
is a gauge in Grafana.

**Temporal** replaces Spring Batch for the refund and chargeback workflows and replaces
hand-rolled orchestration logic for settlement. Temporal gives durable workflow execution:
a workflow that is halfway through a multi-day chargeback lifecycle survives service
restarts, crashes, and deployments. Each step (Spring Batch step equivalent) is a Temporal
activity with configurable retry. The settlement workflow adds timeout timers, escalation
signals, and human approval gates. Temporal's replay model means the workflow history is
the source of truth.

**Debezium CDC** replaces the outbox polling scheduler. Instead of a `@Scheduled` method
scanning the `outbox_events` table every 100ms, Debezium listens to the Postgres WAL
(Write-Ahead Log) and streams every row insert to Kafka the instant it commits. Zero
polling, zero missed events, lower Postgres load.

**Schema Registry + Avro** replace JSON for all Kafka messages. Avro is binary, compact,
and schema-versioned. The Schema Registry enforces BACKWARD compatibility — old consumers
can read new messages because new fields have defaults. Schema evolution errors are
compile-time (Maven plugin generates Java POJOs from `.avsc` files), not runtime NPEs from
missing JSON fields.

**ISO 20022** adds a large-value settlement path for transactions above ₹2,00,000. ISO
20022 is the modern XML/JSON financial messaging standard that India's RTGS and NEFT have
migrated to. The `pacs.008` message (FI to FI Customer Credit Transfer) replaces the batch
CSV submission for large-value settlements. The `camt.053` bank statement (Bank to Customer
Statement) replaces the MT940 mock file in reconciliation. The `prowide-iso20022` library
(Apache 2.0) handles message building and parsing. `LargeValueSettlementPort` is the domain
outbound port — the interface is defined now, the implementation comes post-June.

### DevOps Additions

**Terraform IaC** codifies the entire AWS infrastructure: VPC with public/private subnets
across two availability zones, ECS cluster with task definitions for all services, RDS
PostgreSQL with Multi-AZ in production, ElastiCache Redis, S3 buckets with lifecycle
policies, CloudFront distribution in front of the frontends and reports, IAM roles with
least-privilege policies, Secrets Manager entries. Remote state in S3 with DynamoDB
locking. Workspaces: dev, qa, prod. `terraform plan` runs on every PR touching `infra/`.

**Kubernetes + Helm** runs the system on a local `kind` cluster. Every service has a Helm
chart with Deployment, Service, Ingress, HorizontalPodAutoscaler, PodDisruptionBudget,
ConfigMap, and NetworkPolicy. Resources and limits on every container. Network policies
default-deny with explicit allow between services only.

**ArgoCD GitOps** makes git the single source of truth for cluster state. Merging a PR
that updates a Helm chart value — say, a new Docker image tag — triggers ArgoCD to sync
the cluster automatically. No manual `kubectl apply`. Sync waves ensure databases come up
before caches before services before ingress. The ArgoCD dashboard shows live drift
between what git says should be deployed and what is actually running.

**Argo Rollouts** replaces standard Kubernetes Deployments for acquiring-service and
payment-switch with canary deployments. New version receives 10% of traffic, waits 5
minutes, an `AnalysisTemplate` queries Prometheus for error rate. If error rate exceeds 1%,
the rollout automatically aborts and rolls back. If it passes, traffic shifts to 25%, 50%,
100%. settlement-service and reconciliation-service use blue-green (instant cutover with
no traffic split — batch jobs must not run in parallel on two versions).

**KEDA** adds event-driven autoscaling on top of Kubernetes HPA. The
webhook-dispatcher scales from 0 to N pods based on Kafka consumer group lag — when
`transaction.events` has 1,000 unprocessed messages, KEDA adds pods; when the lag clears,
KEDA scales back to zero. This is dramatically cheaper than always-on pods for bursty workloads.

**Grafana LGTM Stack** (Loki + Grafana + Tempo + Mimir) gives the full observability
picture locally and in staging. All services ship logs to Loki via OpenTelemetry Collector,
traces to Tempo via OTLP, metrics to Prometheus (scraped by Mimir for long-term storage).
Grafana correlates all three: click a high-latency data point on a metrics chart, jump
directly to the trace that caused it, then click into the log lines from that trace span.
Tail-based sampling captures 100% of error traces and 5% of success traces.

**Security CI Pipeline** adds Trivy (container CVE scanning — CRITICAL findings fail the
PR), SonarQube (code quality and security hotspots), Semgrep (OWASP Top 10 SAST rules),
detect-secrets (no credentials committed), OWASP Dependency Check (known CVEs in Maven
and npm dependencies), and weekly OWASP ZAP DAST scans against staging.

**LitmusChaos + k6** close the reliability loop. LitmusChaos experiments run on the kind
cluster: kill a payment-switch pod mid-transaction (does the circuit breaker fire?), inject
30% packet loss on the network (does the timeout reversal trigger?), CPU-stress the
acquiring service (does the HPA scale it up?). k6 load tests establish that the system
handles 500 TPS at p95 under 500ms — this is a blocking gate before any prod deployment.

### AI Additions

**LangGraph multi-agent fraud scoring** builds on the `FraudScoringPort` interface defined
in the domain. The `fraud-scoring-service` is a Python FastAPI service using LangGraph for
orchestration. The state graph has three nodes: `RuleBasedNode` (always runs, under 2ms,
fast gate), `VectorSearchNode` (retrieves 10 similar past fraud cases from pgvector using
HNSW cosine similarity search), and `LLMReasonNode` (Claude Haiku reasons over the
retrieved cases and transaction context). Conditional edges: `LOW` from the rule engine
skips vector search and LLM entirely. `MEDIUM` or `HIGH` triggers the full pipeline async
with a 10ms budget.

**pgvector** adds a vector similarity search extension to the same Postgres instance.
The `fraud_embeddings` table stores 1536-dimensional embeddings (Cohere embed-v3) of
historical fraud cases with an HNSW index on the vector column. Similarity search uses
cosine distance: `ORDER BY embedding <-> $1 LIMIT 10`. HNSW (Hierarchical Navigable Small
World) is an approximate nearest neighbor algorithm — it builds a graph of connections
between vectors at multiple layers, giving sub-millisecond searches on millions of vectors.

**Langfuse** observes every LLM call: traces with node-level spans, input/output captured,
token costs attributed per call, retrieval quality metrics (MRR@10, context precision),
latency percentiles, and cost per transaction. When the model drifts or a prompt change
degrades accuracy, Langfuse shows it before it affects production fraud decisions.

**Evals** test the fraud scoring pipeline against 100 labelled transactions (50 fraud, 50
legitimate) using ragas (faithfulness, answer relevancy, context precision) and promptfoo
(regression suite). These run in CI on every PR touching the fraud service — accuracy
below 90% blocks merge.

**Prompt caching** reduces LLM costs by ~90%. The system prompt containing fraud rules and
domain knowledge (roughly 4,000 tokens) is marked with `cache_control: ephemeral`. Anthropic
caches this prefix — subsequent calls with the same prefix pay only for the transaction-
specific context tokens, not the full prompt every time.

**MCP Server** (`tools/payments-mcp-server`) exposes live payments data to any MCP-
compatible client. Tools: `get_transaction`, `search_transactions`, `get_fraud_rules`,
`get_merchant_profile`, `get_switch_health`, `list_settlement_batches`. Resources: live
transaction SSE feed, fraud rules text resource. Claude Desktop or Claude Code can query
live system state directly during debugging or incident response.

### Frontend Additions

**TypeScript strict migration** upgrades all `.jsx` to `.tsx` with `"strict": true`,
`noUncheckedIndexedAccess`, and full typing throughout — stores, hooks, service layer,
API responses, Zustand state. No `any` anywhere.

**Streaming LLM responses** pipe the fraud scoring reasoning to the simulator event log
in real time. When a HIGH-risk transaction is scored, the ops dashboard shows the LangGraph
nodes firing one by one and the LLM's reasoning tokens streaming in — "Checking velocity
patterns... → Retrieved 8 similar cases from 2023... → Pattern match: impossible travel +
round amount + first transaction on PAN" — ending with the final score. This is a live
end-to-end demo of the full AI stack.

**React Native merchant app** brings the full merchant dashboard to mobile. Built with
Expo (managed workflow) and Expo Router (file-based routing identical to Next.js App Router).
Shares all service layer logic with the web app — same TypeScript types, same React Query
patterns, same Zustand stores. Push notifications via Expo Notifications + Firebase Cloud
Messaging notify the merchant on their phone when a payment arrives. Biometric lock (Face
ID / fingerprint) on app open.

**NFC Tap to Pay on iPhone** turns the merchant's iPhone into a contactless payment
terminal using Apple's `ProximityReader` framework (iOS 15.4+, iPhone XS+). The merchant
opens the app, selects amount, holds the phone — the customer taps their card. The EMV
contactless flow runs through the full acquiring-service stack: ARQC verification in
SoftHSM2, fraud scoring, ISO 8583 0100 to the mock network, `AUTHORIZED` in under 800ms.
Field 22 = 91 (contactless EMV) is already handled in acquiring-service — no backend
changes needed. Android uses `react-native-nfc-manager` with NFC HCE Reader mode for the
same flow.

**Apple Watch companion app** (watchOS + SwiftUI) gives delivery agents and merchants
glanceable payment status on their wrist. Not a terminal — no NFC hardware on Watch.
The bridge is WatchConnectivity between the iOS React Native app and the watchOS app.
When `transaction.authorized` fires, the notification path is: Kafka → notification-service
→ APNs → iOS app → WatchConnectivity → haptic notification on Watch with the amount.

---

## Architecture Decision Record

**Why pure domain with zero dependencies?**
Domain code with Spring annotations cannot be tested with `new MyClass()`. It requires
a Spring context, which requires the full test stack, which makes tests slow and fragile.
Pure Java records are testable in microseconds. When the domain compiles to a JAR with
zero runtime dependencies, you can run 254 unit tests in 2 seconds. The ArchUnit rules
that enforce this are not documentation — they are build constraints enforced in CI.

**Why sealed interfaces for results?**
`AuthorizationResult` is a sealed interface with four permitted subtypes. The compiler
enforces exhaustive pattern matching. If you add a new case — say,
`AuthorizationResult.Expired` — every switch statement in the codebase that handles
`AuthorizationResult` becomes a compile error until it handles the new case. Compare
this to an enum with a string reason, where the new value silently falls through to a
default branch. Sealed interfaces make impossible states impossible at compile time.

**Why records for domain models?**
Records are immutable by design. There are no setters. Thread safety is free. Mutation
returns a new instance: `transaction.withStatus(AUTHORIZED)` creates a new Transaction
record, leaving the original unchanged. This means there is no "accidental mutation"
class of bug. It also means the state machine controls all transitions — there is no
other way to change a status.

**Why transactional outbox instead of Kafka-first?**
If you publish to Kafka and then the DB write fails, the event is published but the state
is not saved — ghost event. If you write to DB and then the Kafka publish fails, the state
is saved but no event fires — silent failure. The transactional outbox writes both the
business record and the outbox entry in the same DB transaction. Either both commit or
both roll back. A separate relay process (later replaced by Debezium CDC) reads confirmed
entries and publishes to Kafka. This is exactly-once delivery at the cost of a polling
loop or CDC connector.

**Why SoftHSM2 instead of mocking crypto?**
The HSM is not an implementation detail — it is the security boundary. ARQC verification
using the real PKCS#11 API against SoftHSM2 exercises the same code path that would run
against a Thales Luna in production. A mock that returns `VERIFIED = true` for all inputs
tests nothing. The HSM operations (ARQC verify, PIN translate, ARPC generate, MAC verify)
are on the critical path and have real latency characteristics that affect the p95 target.

**Why ISO 20022 alongside ISO 8583?**
ISO 8583 is not going away for card-present transactions — it is deeply embedded in POS
terminal firmware, switch infrastructure, and network protocol stacks globally. ISO 20022
is the standard for interbank messaging, large-value transfers, and regulatory reporting.
In India, RTGS and NEFT are already on ISO 20022. A complete payments switch must handle
both. The threshold-based routing (≤₹2L → ISO 8583, >₹2L → ISO 20022) matches how
real systems at Juspay and NPCI-adjacent infrastructure work.

**Why hexagonal architecture over layered?**
Layered architecture (Controller → Service → Repository) creates implicit dependencies
going in one direction. Testing a service means testing through or mocking its
dependencies, which means your test knows about implementation details. Hexagonal
architecture makes dependencies explicit as interfaces (ports), decouples the domain from
infrastructure, and makes every adapter independently swappable. The `db.provider: postgres
| mongo | csv` config property works because the domain `TransactionRepository` interface
has three different adapter implementations. The domain never changes.

---

## Interview Demo Script

**45-second card transaction:** Open simulator → enter PAN and amount → click Pay → watch
the event log: ISO 8583 0100 built, MAC verified, ARQC verified, fraud LOW, `0100 → Visa
switch`, `0110 ← response`, Field 39 = 00 APPROVED, `AUTHORIZED`. Switch to dashboard →
transaction in live feed → click for full ISO 8583 detail drawer.

**45-second QR payment:** Switch to QR mode → enter ₹500 → click Generate → real UPI QR
appears → scan with phone → UPI app opens with amount pre-filled → approve → dashboard
transaction appears instantly via SSE push → MailHog shows webhook email.

**60-second timeout + reversal:** Select Timeout + Reversal scenario → click Pay → watch:
0100 sent → 15 seconds → timeout monitor fires → reversal sent → `REVERSAL_PENDING` →
`REVERSED` → explain race condition handling in the event log.

**45-second settlement:** Ops dashboard → click Run Now on Settlement → Spring Batch starts
→ S3 shows settlement CSV → click Run Now on Reconciliation → three-way match runs →
MailHog shows ops completion email.

**30-second system health:** Ops dashboard shows switch health — VISA primary/secondary UP,
latency 38ms. Kill mock-upstream container → indicator turns red within 60 seconds →
MailHog alert email → restart → watch reconnect with exponential backoff logged.

---

*Last updated: May 2026. Covers system through v1.0.0 (June sprint completion) and
describes all post-June v2.0.0 additions. This document is the narrative companion to
CLAUDE.md, which is the authoritative specification.*
