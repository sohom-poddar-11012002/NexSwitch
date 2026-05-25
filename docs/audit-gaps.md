# NexSwitch Codebase Audit — Gap Tracker
<!-- Generated 2026-05-26 from 10-pass audit + final sweep (85 confirmed gaps) -->
<!-- Status legend: [ ] open  [x] done  [-] skipped/deferred -->

---

## Execution Order

Fix in this sequence: **G0 bugs first** (break at runtime) → **G1 security** → **G2 data integrity** → **G3 resilience** → **G4 API surface** → **G5 observability** → **G6 wiring** → **G7 config** → **G8 code quality** → **G9 testing/CI**

---

## G0 — Runtime Bugs (fix before any other PR)

| # | File | Gap | Priority |
|---|---|---|---|
| [x] N29 | `V16__create_collect_requests.sql:13` | FK `REFERENCES merchants(merchant_id)` — merchants PK is `id`, not `merchant_id`. Flyway will fail on startup. | CRITICAL |
| [x] N30 | 6 service `application.yml` files | `server.port:` is blank in `mock-upstream`, `notification-service`, `payment-switch`, `chargeback-service`, `reconciliation-service`, `settlement-service` — Spring picks random port on each restart. | CRITICAL |
| [x] N31 | `ci-cd.yml:112` | `docker compose push` — `ECR_REGISTRY` is a shell env var never declared in the workflow; push step silently pushes to nothing. | HIGH |
| [x] N20 | `Iso8583TcpServer.java` | `bossGroup`, `workerGroup`, `serverChannelFuture` are non-volatile, no synchronization — race condition on concurrent `start()`/`stop()` calls. | HIGH |
| [x] N47 | `TriggerRunService.java:23` | Non-atomic `get`+`remove` on `ConcurrentHashMap` — `resumeWait()` and `removeExpiredResumes()` can race; one remove silently drops the other. | HIGH |

---

## G1 — Security / Compliance

| # | File | Gap | Priority |
|---|---|---|---|
| [ ] N1 | `Iso8583TcpServer.java` | No TLS on ISO 8583 TCP socket — PAN, ARQC, DUKPT KSN travel in plaintext. Need `SslContext` on Netty bootstrap. | CRITICAL |
| [x] N2 | `SecurityConfig.java` | Empty stub — all REST endpoints unauthenticated. Need `SecurityFilterChain` with JWT/API-key rules, `/actuator/**` restricted, CSRF config. | CRITICAL |
| [x] N70 | All services | CORS completely absent — no `CorsConfigurationSource` bean, no `@CrossOrigin`. Next.js frontends will be blocked by browser preflight. | HIGH |
| [ ] N36 | `docs/specs/security.md §34.2` | RBI card-on-file tokenisation mandate (effective Jan 2022) not implemented — stored PANs must be replaced with network tokens. | HIGH |
| [ ] N23 | Domain / adapters | No tokenization for refund/chargeback — these flows need to re-present the original PAN to the network; SHA-256 hash is one-way and can't be reversed. | HIGH |
| [x] N55 | `SoftHsm2HsmAdapter.java:~102` | PIN block byte array not zeroed in `finally` — on exception before `reencryptUnderZpk()` completes, plaintext PIN is GC-visible in heap. Need `Arrays.fill(pinBlock, (byte)0)` in finally. | MEDIUM |
| [x] N56 | `UpiCollectController.java:~87` | `POST /upi/collect/outcome` does not verify that the outcome matches the `collectId` owner — caller who guesses a valid `collectId` can POST a fake outcome. Need ownership/signature check. | MEDIUM |
| [x] N10 | `application.yml` | No request body size limits — `spring.servlet.multipart.max-file-size` / `server.tomcat.max-http-post-size` unset; opens door to OOM via large payload. | MEDIUM |
| [ ] N15 | `next.config.ts` | Empty — no security headers (CSP, `X-Frame-Options`, HSTS) on any of the three Next.js frontends. | MEDIUM |
| [ ] N21 | `SwitchTcpClient.java` | `terminal-simulator` connects with `new Socket()` (plaintext). Should use TLS to match the server-side fix in N1. | LOW |

---

## G2 — Data Integrity

| # | File | Gap | Priority |
|---|---|---|---|
| [ ] G2-1 | Adapters / domain | **Transactional Outbox not implemented** — `outbox_events` table exists (V9) but no Java writer, poller, or relay. `Transaction.raiseEvent()` raises events; `pullDomainEvents()` is never called in production; Kafka never receives them. | CRITICAL |
| [ ] N18 | All use-case classes | `pullDomainEvents()` never called after `save()` — domain events raised by `TransactionStateMachine` are silently discarded. Blocked by G2-1. | HIGH |
| [ ] G2-2 | `V1__create_transactions.sql` | No `CHECK (amount > 0)` — zero and negative amounts pass all the way to DB. | HIGH |
| [ ] N32 | `V1__create_transactions.sql` | `amount` column has no `CHECK` constraint. Add `CONSTRAINT positive_amount CHECK (amount > 0)`. | HIGH |
| [ ] N33 | `V8__create_reserve_accounts.sql` | No `CHECK (balance >= 0)` — reserve balance can go negative at DB level. Add `CONSTRAINT non_negative_balance CHECK (balance >= 0)`. | HIGH |
| [ ] N35 | DB migrations | Two tables spec'd in `database.md:319-337` but never migrated: `pan_atc_watermarks` (ATC replay detection) and `network_success_rates` (routing fallback decisions). | MEDIUM |
| [ ] G2-3 | `V7__create_reconciliation.sql` | No index on `reconciliation_runs(status)` — settlement queries filter by status; full table scan at scale. Add `CREATE INDEX idx_recon_status ON reconciliation_runs(status)`. | MEDIUM |
| [ ] N34 | `V7__create_reconciliation.sql` | Same as G2-3 above. | MEDIUM |
| [ ] N57 | `WebhookDeliveryService.java:~58` | `merchantRepository.findById()` inside Kafka consumer not wrapped in try-catch — transient DB failure throws unchecked, crashes consumer thread, halts all webhook delivery. | HIGH |
| [ ] N78 | `WebhookConfig.java:35` | **`RestClient` for outbound webhook delivery has no connect or read timeout** — `RestClient.builder().defaultHeader(...).build()` with no `requestFactory`. CLAUDE.md rule 4 mandates webhook 5s timeout. A slow or hung merchant endpoint will block the delivery thread indefinitely. Add `HttpComponentsClientHttpRequestFactory` with 5s connect + read timeouts. | HIGH |

---

## G3 — Resilience

| # | File | Gap | Priority |
|---|---|---|---|
| [ ] G3-1 | Domain / adapters | **Terminal + routing rule caches missing** — every authorization hits PostgreSQL for terminal lookup and routing rules. Add Caffeine L1 + Redis L2 with SETNX anti-stampede (same pattern as `CachingBinLookupAdapter`). | HIGH |
| [ ] G3-2 | `ResilientNetworkAuthAdapter.java` | **`@Retry` missing** — transient network timeouts fail immediately with no retry. Add `@Retry(name="networkAuth", maxAttempts=2, waitDuration=200ms)` alongside existing `@CircuitBreaker` and `@Bulkhead`. | HIGH |
| [ ] N4 | `CachingBinLookupAdapter`, `CachingMerchantRepository`, `RedisIdempotencyAdapter` | No Redis fallback — if Redis is down these adapters throw; should fall through to delegate/DB instead. | HIGH |
| [ ] N52 | `application.yml:79,91` | `failureRateThreshold: 50` on HSM and network circuits — 50% failure rate before opening is far too permissive for payments. Reduce to 20–30%. | MEDIUM |
| [ ] N53 | `application.yml:83` | `waitDurationInOpenState: 30s` for HSM — 30 seconds of open circuit means 30s of failed authorizations. Reduce to 5–10s. | MEDIUM |

---

## G4 — API Surface

| # | File | Gap | Priority |
|---|---|---|---|
| [ ] G4-1 | `GlobalExceptionHandler.java` | **`ConstraintViolationException` not handled** (N6) — path/query param validation failures return 500 instead of 400 with a structured body. | MEDIUM |
| [ ] N5 | `UpiCollectController`, `QrController` | `POST /upi/collect` and `POST /qr/generate` not idempotent — no `X-Idempotency-Key` header accepted; duplicate requests create duplicate records. | MEDIUM |
| [ ] G4-2 | All services | **OpenAPI annotations missing** — no `@Operation`/`@Schema` on any controller; `/swagger-ui.html` returns empty spec. | MEDIUM |
| [ ] G4-3 | `webhook-dispatcher` | **DLQ not replayable** — no endpoint to re-inject dead-letter webhook events; failed deliveries are permanently lost. | MEDIUM |
| [ ] G4-4 | Inbound webhook path | **Inbound webhook signature verify endpoint missing** — merchants can't self-test their HMAC-SHA256 setup. | LOW |
| [ ] N75 | All 8 controllers | **`@Validated` missing at class level** — `QrController`, `UpiCollectController`, `UpiCreditController`, `LiveStreamController`, `RecorderController`, `RunController`, `ScenarioController`, `SuiteController` all lack `@Validated`. Any `@NotBlank`/`@Min` added to `@PathVariable` or `@RequestParam` params will silently never trigger `ConstraintViolationException`. | MEDIUM |
| [ ] N76 | `WebhookConfig.java:83` | **No `DefaultErrorHandler` on `webhookKafkaListenerContainerFactory`** — a poison-pill message (malformed JSON / schema mismatch) causes infinite redelivery, blocking the consumer permanently. Need `factory.setCommonErrorHandler(new DefaultErrorHandler(dlqPublisher, new FixedBackOff(1000L, 3)))`. | HIGH |
| [ ] N9 | All services | Management port not separated — Actuator shares the application port (`8080`); in prod it should be on a separate internal port (e.g. `8081`) unreachable from the public LB. | MEDIUM |
| [ ] N72 | All services | Actuator endpoint exposure not configured — no `management.endpoints.web.exposure.include` in any `application.yml`; the empty `SecurityConfig` means any future `include=*` immediately exposes everything unauthenticated. Set `include: health,info` now. | MEDIUM |
| [ ] N42 | `docker-compose.yml:319` | `NEXT_PUBLIC_QA_API` set at runtime — Next.js bakes `NEXT_PUBLIC_*` vars at build time; this env var is silently ignored. Must be set as a build arg. | LOW |

---

## G5 — Observability

| # | File | Gap | Priority |
|---|---|---|---|
| [ ] G5-1 | All services | **No MDC context filter** — logs carry no `traceId`, `transactionId`, `merchantId`, `cardLast4`. Add a `OncePerRequestFilter` that seeds MDC from request headers / thread-local. | MEDIUM |
| [ ] G5-2 | All services | **No structured JSON logging** — plain-text log lines break ELK/Datadog ingestion. Add `logstash-logback-encoder` and a `logback-spring.xml` for prod profile. | MEDIUM |
| [ ] N13 | All services | No custom business metrics — no `payment.authorized.count`, `payment.declined.count`, `fraud.blocked.count` Prometheus counters/histograms. | MEDIUM |
| [ ] N82 | All services | No custom `HealthIndicator` for Redis, Kafka, or HSM — only Spring's auto-configured beans. HSM operational state (slot availability, key presence) is never surfaced to `/actuator/health/readiness`. Add `HsmHealthIndicator`, `KafkaHealthIndicator` (custom, not Spring's default ping) with timeout. | MEDIUM |

---

## G6 — Wiring Gaps

| # | File | Gap | Priority |
|---|---|---|---|
| [ ] N22 | `KafkaConfig.java`, `RedisConfig.java`, `ObservabilityConfig.java` | All three are empty stubs — no `KafkaTemplate`, no `RedisTemplate` override, no Micrometer/Prometheus wiring beyond auto-config. | MEDIUM |
| [ ] N11 | `KafkaConfig.java` | Kafka topics auto-created by broker — no `NewTopic` beans means single partition + replication factor 1 in production. Define all topics programmatically with correct partition counts. | MEDIUM |
| [ ] N49 | `MerchantCacheInvalidationListener.java` | Consumes `merchant.config.updated` topic but nothing in the codebase publishes to it — cache invalidation is permanently broken. | HIGH |
| [ ] N50 | Domain port | `NotificationPort` has zero adapter implementations — declared in domain, never wired. | MEDIUM |
| [ ] N51 | Domain port | `WebhookDispatchPort` has zero adapter implementations — declared in domain, never wired. | MEDIUM |
| [ ] N84 | Domain / adapters | **`audit_log` table has no Java writer** — V3 migration creates the table; CLAUDE.md §6 requires "Audit log written if transaction state changed." No `AuditPort`, no adapter, no domain event publisher exist anywhere. Every state transition silently skips the audit trail. | HIGH |
| [ ] N24 | Domain / adapters | `ProcessRefundUseCase` and `RefundPort` are defined but zero adapters implement `RefundPort`; refund is a no-op end-to-end. | HIGH |
| [ ] N48 | Use-case layer | `ReconcileUseCase` has zero callers in the codebase — reconciliation is never triggered. | MEDIUM |
| [ ] N54 | `AdapterConfig.java` | `ReconcileUseCase`, `ProcessRefundUseCase`, `NotificationPort`, `WebhookDispatchPort` not wired as `@Bean` — Spring context will fail to autowire any class that depends on them. | HIGH |
| [ ] N27 | Services | 5 services are empty stubs: `notification-service`, `chargeback-service`, `reconciliation-service`, `settlement-service`, `merchant-simulator` — no controllers, no consumers, no schedulers. | MEDIUM |
| [ ] N16 | `SettlementService.java` | `@EnableScheduling` present but no chunk-based `Job`, `Step`, or `ItemReader` — Spring Batch settlement is a stub. | MEDIUM |

---

## G7 — Configuration

| # | File | Gap | Priority |
|---|---|---|---|
| [ ] N3 | All services | `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase` absent — in-flight ISO 8583 transactions are dropped on pod restart. | HIGH |
| [ ] N7 / N71 | `application.yml` (all services) | HikariCP missing `leakDetectionThreshold`, `idleTimeout`, `maxLifetime`, `keepaliveTime` — defaults cause stale connections after pgBouncer/RDS Proxy resets. Set: `idle-timeout: 600000`, `max-lifetime: 1800000`, `keepalive-time: 60000`. | MEDIUM |
| [ ] N8 | All services `application.yml` | `spring.threads.virtual.enabled=true` absent — JDK 26 available but Tomcat still uses platform threads. One-liner. | LOW |
| [ ] N12 | `JpaTerminalRepository.java` | `findByMerchantId()` returns `List<TerminalEntity>` — 10K terminals for one merchant = full table load. Add `Pageable` parameter. | LOW |
| [ ] N37 | `ci-cd.yml:35` | CI uses `java-version: 26` but `infrastructure.md §18.1` references JDK 21 — inconsistency will silently differ from local dev. Align to 26 everywhere. | LOW |
| [ ] N19 | Root / services | All 12 Dockerfiles are missing — `docker-compose.yml` and `ci-cd.yml` reference them; `docker compose build` fails immediately. | HIGH |
| [ ] N81 | `application.yml` (all services with Kafka consumers) | `max.poll.records` and `max.poll.interval.ms` not configured — default `max.poll.records=500` with any slow processor (e.g. DB write per record) risks exceeding `max.poll.interval.ms=300s`, causing the consumer to be evicted from the group and triggering endless rebalance. Set `max-poll-records: 50` and explicit `max-poll-interval-ms: 300000`. | MEDIUM |

---

## G8 — Code Quality

| # | File | Gap | Priority |
|---|---|---|---|
| [ ] G8-1 | `RoutingEngine.java:20-24` | `rules.stream().filter().min()` is O(n) per transaction — at high TPS this is a hot path. Replace with `HashMap<String, RoutingRule>` keyed on BIN6, populated once at startup. | LOW |
| [ ] G8-2 | `FileStoragePort.java` | `store(String filename, byte[] content, FileCategory)` buffers the entire file in heap. Split into: `store(String, InputStream, FileCategory)` for server-generated files and `generatePresignedPutUrl(String, FileCategory)` for user uploads → direct S3 PUT. | LOW |
| [ ] G8-3 | `FeeWaterfallCalculator.java`, `TransactionStatus.java` | `INTERCHANGE`, `ASSESSMENT`, `VALID_TRANSITIONS` use `Map.of()` / `Map.ofEntries()` with enum keys — should be `EnumMap` for array-indexed O(1) lookup and explicit exhaustiveness. | LOW |
| [ ] G8-4 | `FeeWaterfallCalculator.java` | `MINIMUM_MDR = new BigDecimal("2.00")` hardcoded — should be injected from config so it can differ per environment/merchant type without a code change. | LOW |
| [ ] N17 | `IsoTlvParser.java` | `new HashMap<>()` for TLV tag map — 20-40 tags expected; pre-size with `new HashMap<>(64)` to avoid a rehash mid-parse on every transaction. | LOW |
| [ ] N14 | `Iso8583TcpServer.java` | `SO_KEEPALIVE`, `TCP_NODELAY`, `SO_BACKLOG` not set on Netty bootstrap — silent connection drops under load and latency spikes. | LOW |
| [ ] N25 | `ScenarioRunner.java` | `stan.next()` using non-atomic counter — concurrent scenario runs share the counter; duplicate STANs corrupt test results. Replace with `AtomicLong`. | LOW |
| [ ] N26 | `FeeWaterfallCalculator.java` | `calculateWithDcc()` exists but `dccFxMargin` is never injected — always returns null/zero; DCC path is silently broken. | LOW |
| [ ] N46 | `ScenarioExecutionEngine.java:232` | `Thread.ofVirtual().start(runnable)` with no future — if the virtual thread throws, the error is silently swallowed. Wrap with `Thread.ofVirtual().start(() -> { try { runnable.run(); } catch (Throwable t) { log.error(..., t); } })`. | MEDIUM |
| [ ] N58 | `RedisQrSessionAdapter.java:~86` | `serialize()` catches `Exception` and silently returns `"null"` — on deserialize, corrupt Redis entry is indistinguishable from a genuine miss. Log at WARN and propagate. | LOW |
| [ ] N77 | `AuthorizationService`, `InitiateCollectService`, `QRSessionManager`, `Transaction`, `CollectRequest`, `QRSession`, `DomainEvent` | **`Instant.now()` hardcoded throughout domain** — no `Clock` injection anywhere. `isExpired()`, `withStatus()`, event timestamps all pin to wall time; unit tests cannot control time, making time-sensitive assertions flaky. Inject `java.time.Clock` into domain services; pass it to record factory methods. | LOW |
| [ ] N80 | `CachingMerchantRepository.java:53`, `CachingBinLookupAdapter.java:50`, `RedisQrSessionAdapter.java:36`, `RestTestAdapter.java:28` | `new ObjectMapper()` raw instantiation in 4 places — bypasses the Spring-managed bean that has `JavaTimeModule`, `FAIL_ON_UNKNOWN_PROPERTIES=false`, and other auto-configured modules. Any `Instant`/`LocalDate` field will throw `InvalidDefinitionException` at runtime. Inject `ObjectMapper` via constructor instead. | MEDIUM |
| [x] N83 | `WebhookConfig.java:24-31` | **`@Value` on fields** — `bootstrapServers`, `dlqTopic`, `maxAttempts` are all `private` field injections. ArchUnit Rule 10 (`DomainArchitectureTest`) bans `@Value` on fields and scans all `com.nexswitch.*` packages — this **will fail CI**. Move all three to `@Configuration` constructor parameters. | HIGH |
| [ ] N85 | `PostgresCollectRequestRepository.java:35-40` | `update()` does `findByCollectId()` + `jpa.save()` without `@Transactional` — two separate JPA calls with no transaction boundary. Concurrent UPI Collect outcome POSTs on the same `collectId` can interleave: both read the same entity, one write is lost. Add `@Transactional`. | HIGH |

---

## G9 — Testing / CI

| # | File | Gap | Priority |
|---|---|---|---|
| [ ] G9-1 | `adapters/pom.xml`, `services/*/pom.xml` | JaCoCo `<check>` goal only enforces on `domain` module — adapters and services have zero coverage enforcement. Add `<check>` with `≥80%` line coverage to each. | MEDIUM |
| [ ] N38 | QA scenarios | Partial reversal scenario missing — spec `payment-flows.md §5.8` describes auth ₹10k → capture ₹8k → reverse ₹2k. No scenario exercises this path. | MEDIUM |
| [ ] N39 | `services/qa-orchestrator/src/main/resources/scenarios/` | `postgres-slow.yml` referenced in `infrastructure-run.yml` but file doesn't exist — that suite will error on load. | LOW |
| [ ] N40 | QA scenarios | EMI, Payment Links, Virtual Accounts have zero QA scenarios — all three are spec'd in `industry-features.md`. | LOW |
| [ ] N41 | `50-concurrent-auths-same-card.yml` | Concurrent auth scenario doesn't actually assert idempotency — it sends different STANs so each is treated as a unique transaction, not a duplicate. Needs same STAN to test idempotency. | LOW |
| [ ] N43 | `docker-compose.yml:315` | `frontend-qa-portal` service has no `healthcheck` — any `depends_on` with `condition: service_healthy` will hang indefinitely. | LOW |
| [ ] N44 | `ci-cd.yml:42` | `mvn verify` has no `timeout-minutes` — one flaky test blocks the runner indefinitely, burning GitHub Actions minutes. Add `timeout-minutes: 20`. | LOW |
| [ ] N79 | `IntegrationTestBase.java:50`, `docker-compose.yml` | **Floating `:latest` image tags** — `apache/kafka:latest` (Testcontainers), `mailhog/mailhog:latest`, `localstack/localstack:latest`, `dpage/pgadmin4:latest`, `provectus/kafka-ui:latest`, `redis/redisinsight:latest` in docker-compose. A silent upstream major-version bump breaks CI or local dev with no code change. Pin every image to a specific digest or version tag. | LOW |
| [ ] N45 | `ci-cd.yml:43` | Missing `--fail-at-end` — if one module fails hard, downstream module results are never reported; harder to diagnose multi-module failures. | LOW |

---

## Summary

| Group | Count | Status |
|---|---|---|
| G0 Runtime Bugs | 5 | [x] DONE |
| G1 Security | 10 | 6/10 done |
| G2 Data Integrity | 10 | [ ] |
| G3 Resilience | 5 | [ ] |
| G4 API Surface | 10 | [ ] |
| G5 Observability | 4 | [ ] |
| G6 Wiring | 11 | [ ] |
| G7 Configuration | 7 | [ ] |
| G8 Code Quality | 14 | [ ] |
| G9 Testing / CI | 9 | [ ] |
| **Total** | **85** | **0 / 85 done** |

> N28 was not assigned in the audit (numbering gap in the original pass). N55–N58 are code-level bugs found in the full file sweep. N80–N82 added in the final cross-cutting pass. N83–N85 added from targeted pattern checks (ArchUnit @Value, audit_log writer, @Transactional).
