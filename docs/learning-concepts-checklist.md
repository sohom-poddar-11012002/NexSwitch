# Learning Concepts Checklist — Payments Platform

A self-study guide covering every concept used in this project.
Check off each item as you understand it. Items marked 🔥 are frequently asked in SDE2 interviews.

---

## 1. Java Language Features

### Java Basics
- [ ] Classes vs interfaces vs abstract classes — when to use each
- [ ] Access modifiers: `public`, `protected`, `private`, package-private
- [ ] `final` keyword — on variables, methods, classes
- [ ] `static` keyword — static fields, static methods, static nested classes
- [ ] `null` handling — why NullPointerException exists and how to avoid it

### Java Collections
- [ ] `List`, `Set`, `Map` — interfaces vs implementations (ArrayList, HashSet, HashMap)
- [ ] `Map.ofEntries()` — immutable maps, used in `TransactionStatus` valid transitions
- [ ] Iterating with `forEach`, streams, `entrySet()`

### Java 8+ Features
- [ ] Lambda expressions — `(x) -> x.doSomething()`
- [ ] Stream API — `filter()`, `map()`, `collect()`, `forEach()`
- [ ] `Optional<T>` — wraps a value that may be absent, avoids null checks
- [ ] Method references — `SomeClass::method`
- [ ] `Instant` — timezone-safe timestamp (used everywhere instead of `Date`)
- [ ] `Duration` — time intervals like `Duration.ofSeconds(15)`
- [ ] `LocalDate`, `LocalDateTime` — for settlement dates and batch runs

### Java 17-21 Features (used heavily here)
- [ ] **Records** 🔥 — immutable data classes: `public record Money(BigDecimal amount, Currency currency) {}`
  - Why: eliminates boilerplate getters/equals/hashCode/toString
  - Used for: Money, MerchantId, Transaction, QRSession, and all domain models
- [ ] **Compact constructors** — validation inside records without repeating field names
- [ ] **Sealed interfaces** 🔥 — `sealed interface AuthorizationResult permits Approved, Declined, Unknown`
  - Why: exhaustive pattern matching — compiler forces you to handle every case
  - Used for: AuthorizationResult, all result types
- [ ] **Pattern matching switch** 🔥 — `switch (result) { case Approved a -> ...; case Declined d -> ...; }`
  - Why: replaces instanceof chains, exhaustive check at compile time
- [ ] **Text blocks** — multiline strings with `"""`
- [ ] **Virtual threads** 🔥 — lightweight threads managed by JVM (not OS)
  - Why: 1 million concurrent virtual threads vs ~10k OS threads
  - Enabled in Spring Boot 3.2+ via `spring.threads.virtual.enabled=true`
- [ ] **`instanceof` pattern matching** — `if (obj instanceof String s) { s.length(); }`

### BigDecimal — Money Arithmetic
- [ ] Why `double` is wrong for money — `0.1 + 0.2 = 0.30000000000000004`
- [ ] `new BigDecimal("6000.00")` — always use String constructor, never `double`
- [ ] `setScale(2, RoundingMode.HALF_UP)` — controls decimal places and rounding
- [ ] `compareTo()` vs `equals()` — BigDecimal equality is scale-sensitive
- [ ] `divide(divisor, 2, RoundingMode.HALF_UP)` — always specify scale when dividing
- [ ] `NUMERIC(15,2)` in Postgres — the SQL equivalent of BigDecimal

### Exception Handling
- [ ] Checked vs unchecked exceptions — `Exception` vs `RuntimeException`
- [ ] `throw new IllegalArgumentException("message")` — used in all value objects
- [ ] Custom exceptions — `InvalidStateTransitionException` in this project
- [ ] `try-finally` — used for MDC.clear() to prevent thread pool contamination

### Enums
- [ ] Basic enums — `enum PaymentMethod { CARD_CHIP, CONTACTLESS, UPI_QR }`
- [ ] Enums with fields and methods — `TransactionStatus` with valid transitions map
- [ ] `Map.ofEntries(entry(STATUS, Set.of(NEXT_STATUS...)))` — the transitions map pattern

### Concurrency
- [ ] `CompletableFuture` — async computation with timeout support
- [ ] Thread safety of records — immutable objects are safe by design
- [ ] `@Scheduled(fixedDelay = 100)` — Spring scheduler for timeout monitor
- [ ] MDC (Mapped Diagnostic Context) — thread-local log context, cleared in `finally`

---

## 2. Build Tools & Project Structure

### Maven
- [ ] `pom.xml` — Project Object Model, describes the project and dependencies
- [ ] Multi-module Maven — parent pom.xml with `<modules>`, child poms inherit versions
- [ ] `<dependencyManagement>` — declare versions in parent, children inherit without repeating
- [ ] `<scope>test</scope>` — dependency only available during tests, not in prod JAR
- [ ] Build lifecycle: `compile → test → package → install → deploy`
- [ ] `mvn install` — compiles, tests, and installs JAR to local `~/.m2` repo
- [ ] `mvn test -pl domain` — runs tests for one module only
- [ ] `-DskipTests` — skip tests during build (use sparingly)
- [ ] BOM (Bill of Materials) — Spring Boot BOM manages all dependency versions together

### Maven Plugins Used
- [ ] `maven-compiler-plugin` — sets Java version (21), enables `--parameters`
- [ ] `maven-surefire-plugin` — runs unit tests, excludes `@Tag("integration")`
- [ ] `maven-failsafe-plugin` — runs integration tests in `verify` phase
- [ ] `jacoco-maven-plugin` — measures code coverage, fails build if below 90%

---

## 3. Architecture Patterns

### Hexagonal Architecture (Ports & Adapters) 🔥
- [ ] The three layers: Domain Core → Application → Adapters
- [ ] **Domain** — pure Java, zero external imports (no Spring, no JPA, no Kafka)
- [ ] **Ports** — interfaces defined in domain that adapters implement
  - Inbound ports: what the domain exposes (`ProcessPaymentUseCase`)
  - Outbound ports: what the domain needs (`TransactionRepository`, `HsmPort`)
- [ ] **Adapters** — infrastructure implementations (Postgres, Redis, Kafka, jPOS)
- [ ] Why: swap Postgres for MongoDB by writing a new adapter — domain unchanged
- [ ] ArchUnit enforces this at build time — CI fails if domain imports Spring

### SOLID Principles 🔥
- [ ] **S — Single Responsibility**: one class, one job. `FraudEngine` only scores fraud.
- [ ] **O — Open/Closed**: add new network by adding a class, not editing `RoutingEngine`
- [ ] **L — Liskov Substitution**: `WireMockNetworkAdapter` and `RazorpayAdapter` are interchangeable
- [ ] **I — Interface Segregation**: `AuthorizationPort` is separate from `RefundPort`
- [ ] **D — Dependency Inversion**: domain depends on `TransactionRepository` interface, not Postgres

### Constructor Injection 🔥
- [ ] Why constructor injection: dependencies are `final`, explicit, testable with `new`
- [ ] Why NOT `@Autowired` on fields: hidden dependencies, can't use `new` in tests
- [ ] `Objects.requireNonNull(dependency)` in constructor — fail fast

### Repository Pattern
- [ ] Domain port interface → adapter implementation → JPA entity → Spring Data interface
- [ ] Why: domain never imports JPA; entities never leak into domain
- [ ] MapStruct mapper: only bridge between domain records and JPA entities

### Command/Query Separation (CQRS-lite) 🔥
- [ ] Commands change state, return minimal acknowledgement
- [ ] Queries return data, have zero side effects
- [ ] `TransactionRepository` (write) vs `TransactionQueryRepository` (readOnly)
- [ ] `@Transactional(readOnly=true)` routes to Postgres read replica

### Domain Events 🔥
- [ ] Domain raises events but doesn't publish them directly
- [ ] `transaction.pullDomainEvents()` — collect events, then publish after save
- [ ] Why: save first, then publish — if save fails, no ghost events

### Transactional Outbox Pattern 🔥
- [ ] Problem: Kafka publish + DB save in same operation, but different systems
- [ ] Solution: save event to `outbox_events` table in same DB transaction
- [ ] Relay scheduler polls outbox and publishes to Kafka
- [ ] If DB transaction rolls back, event is never published — consistency guaranteed

### Optimistic Locking 🔥
- [ ] `@Version Long version` on JPA entity
- [ ] Two concurrent updates: first wins, second gets `OptimisticLockException`
- [ ] No DB locks held → high throughput, no deadlocks

### Value Objects / No Primitive Obsession 🔥
- [ ] Wrap primitives in self-validating types: `MerchantId`, `Money`, `PanHash`
- [ ] Why: compiler catches `(terminalId, merchantId)` argument swap; String can't
- [ ] Validation at construction — invalid value never exists in the system

---

## 4. Spring Boot

### Core Concepts
- [ ] `@SpringBootApplication` — combines @Configuration, @ComponentScan, @EnableAutoConfiguration
- [ ] Auto-configuration — Spring Boot guesses beans based on classpath dependencies
- [ ] `application.yml` vs `application.properties` — YAML is more readable for nested config
- [ ] Spring profiles — `local`, `dev`, `qa`, `prod` with different config per environment
- [ ] `@Value("${property.name}")` — inject config values
- [ ] `${ENV_VAR:default}` — environment variable with fallback

### Bean Wiring
- [ ] `@Component`, `@Service`, `@Repository` — component scanning annotations
- [ ] `@Configuration` + `@Bean` — explicit bean definitions (preferred in adapters module)
- [ ] `@ConditionalOnProperty` — conditionally register a bean based on config
  - Used for: `hsm.provider: mock` → `MockHsmAdapter`, `hsm.provider: softhsm` → `SoftHsmAdapter`
- [ ] Bean lifecycle — singleton by default, one instance per Spring context

### Spring Data JPA
- [ ] `@Entity`, `@Id`, `@GeneratedValue` — JPA entity annotations
- [ ] `@Version` — optimistic locking
- [ ] `JpaRepository<Entity, ID>` — Spring Data generates SQL automatically
- [ ] `@Query("SELECT t FROM ...")` — custom JPQL queries
- [ ] `@Transactional` — wraps method in a DB transaction
- [ ] `@BatchSize(size=25)` — prevents N+1 by batching collection loads
- [ ] Fetch types: EAGER (load immediately) vs LAZY (load when accessed)

### Spring Data Redis
- [ ] `RedisTemplate` — low-level Redis operations
- [ ] `opsForValue()`, `opsForSet()`, `opsForHash()` — different data structure operations
- [ ] TTL (Time-To-Live) — automatic key expiry: `Duration.ofMinutes(5)`
- [ ] `setIfAbsent()` — Redis SETNX, used for distributed locks and idempotency

### Spring Kafka
- [ ] `@KafkaListener(topics="transaction.events")` — consume messages from topic
- [ ] `KafkaTemplate.send(topic, key, value)` — produce messages
- [ ] Manual offset commit — `enable-auto-commit: false`, commit only after processing
- [ ] Why manual commit: if processing fails, event stays in topic for retry

### Spring Batch
- [ ] Steps: Reader → Processor → Writer (chunk-oriented processing)
- [ ] `JpaPagingItemReader` — reads DB in pages, memory-efficient
- [ ] Restart from checkpoint — if job fails at step 3, restart at step 3
- [ ] `@Scheduled(cron = "0 30 23 * * *")` — cron expression for daily 23:30 run

### Spring Actuator
- [ ] `/actuator/health` — reports DB, Redis, Kafka, custom indicators
- [ ] Liveness vs Readiness probes — Kubernetes/ECS restart vs ALB routing
- [ ] `HealthIndicator` interface — implement for custom checks (HSM, network switch)
- [ ] Prometheus metrics endpoint — `/actuator/prometheus`

### Spring Security
- [ ] Filter chain — requests pass through ordered filters
- [ ] `ApiKeyAuthFilter` — validates `X-Api-Key` header on acquiring REST API
- [ ] mTLS (mutual TLS) — both client and server present certificates

---

## 5. Testing

### JUnit 5
- [ ] `@Test` — mark a method as a test
- [ ] `@ParameterizedTest` + `@MethodSource` / `@CsvSource` — run test with multiple inputs
  - Used for: every valid and invalid `TransactionStatus` transition
- [ ] `@BeforeEach`, `@AfterEach` — setup/teardown per test
- [ ] `@Tag("unit")`, `@Tag("integration")`, `@Tag("chaos")` — categorize tests
- [ ] `assertThrows()` vs `assertThatThrownBy()` — check exception is thrown

### AssertJ
- [ ] `assertThat(value).isEqualTo(expected)` — fluent assertion library
- [ ] `isEqualByComparingTo()` — BigDecimal comparison ignoring scale
- [ ] `isInstanceOf(SomeException.class)` — type check
- [ ] `hasMessageContaining("text")` — check exception message
- [ ] `containsExactlyInAnyOrder()` — set equality without order

### Mockito
- [ ] `@Mock` — creates a mock (fake) implementation of an interface
- [ ] `when(mock.method()).thenReturn(value)` — stub return values
- [ ] `verify(mock).method(argument)` — assert method was called
- [ ] `@InjectMocks` — injects mocks into the class under test
- [ ] Why: test use cases in isolation without real DB/Kafka

### TDD (Test-Driven Development) 🔥
- [ ] Red → Green → Refactor cycle
- [ ] Write the failing test first (it doesn't compile yet — that's fine)
- [ ] Write minimum code to make test pass
- [ ] Refactor while keeping tests green
- [ ] Why: forces you to design API before implementation

### ArchUnit 🔥
- [ ] `@AnalyzeClasses(packages="com.payments")` — scan all classes
- [ ] `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAPackage("org.springframework..")`
- [ ] Run as regular JUnit test — architecture violations = test failure = CI failure
- [ ] Why: prevents accidental architecture erosion as codebase grows

### JaCoCo
- [ ] Instruments bytecode to track which lines were executed during tests
- [ ] `COVEREDRATIO > 0.90` — build fails if coverage drops below 90%
- [ ] Report: `target/site/jacoco/index.html`
- [ ] Why: forces meaningful test coverage, not just "tests exist"

### Testcontainers 🔥
- [ ] Starts real Docker containers (Postgres, Redis, Kafka) for integration tests
- [ ] `@Container` singleton — one container per JVM, shared across all tests
- [ ] `@DynamicPropertySource` — injects container ports into Spring config
- [ ] Why: tests against real infrastructure, not mocks that behave differently

### WireMock
- [ ] HTTP mock server for testing webhook delivery, network adapters
- [ ] `stubFor(post(url).willReturn(aResponse().withStatus(200)))` — define stubs
- [ ] `withFixedDelay(20_000)` — simulate timeout scenarios
- [ ] `verify(postRequestedFor(url))` — assert request was made

---

## 6. Databases

### PostgreSQL
- [ ] Relational database — tables, rows, columns, relationships via foreign keys
- [ ] `UUID PRIMARY KEY DEFAULT gen_random_uuid()` — auto-generated unique IDs
- [ ] `NUMERIC(15,2)` — exact decimal storage for money (never `FLOAT` or `DOUBLE`)
- [ ] `TIMESTAMPTZ` — timezone-aware timestamps (always use over `TIMESTAMP`)
- [ ] `JSONB` — binary JSON column for flexible event data (audit log, outbox)
- [ ] Indexes 🔥 — speeds up queries but slows inserts; add only for actual query patterns
- [ ] Partial indexes — `WHERE status IN ('CAPTURED', 'SETTLEMENT_PENDING')` — smaller index
- [ ] `REVOKE UPDATE, DELETE ON audit_log` — enforce append-only at DB level
- [ ] Connection pools (HikariCP) — reuse connections; creating new connections is expensive

### Flyway
- [ ] Database migration tool — SQL files executed in order on startup
- [ ] `V1__create_transactions.sql`, `V2__create_merchants.sql` — versioned migrations
- [ ] **Never modify existing migrations** — add new `Vn` file for changes
- [ ] Flyway tracks which migrations have run in `flyway_schema_history` table
- [ ] Only `acquiring-service` has `spring.flyway.enabled: true`

### ACID Properties 🔥
- [ ] **Atomicity** — transaction succeeds completely or not at all
- [ ] **Consistency** — DB moves from one valid state to another
- [ ] **Isolation** — concurrent transactions don't interfere
- [ ] **Durability** — committed data survives crashes
- [ ] Optimistic locking uses Isolation without holding locks

### Query Optimization
- [ ] N+1 problem — loading N parents then querying N times for children
- [ ] `JOIN FETCH` — load children in same query as parents
- [ ] `@BatchSize(25)` — load children in batches of 25 (one `IN` query)
- [ ] Projections — `SELECT id, amount, status` instead of `SELECT *`
- [ ] `EXPLAIN ANALYZE` — show query execution plan (how Postgres runs your SQL)

---

## 7. Redis

### Core Concepts
- [ ] Key-value store — fast in-memory database, persistence optional
- [ ] Data structures: String, Hash, List, Set, Sorted Set
- [ ] TTL (Time-To-Live) — keys auto-expire after set duration
- [ ] Why Redis for sessions: sub-millisecond reads, built-in TTL, shared across instances

### Patterns Used in This Project
- [ ] Idempotency store — `idempotency:{stan}:{terminalId}:{date}` TTL 24h
- [ ] BIN cache — L2 cache between Caffeine (L1) and Postgres (L3)
- [ ] QR session — `qr:session:{txnRef}` TTL 5 minutes
- [ ] Correlation store — `correlation:{arn}:{stan}` TTL 30s, for matching responses
- [ ] Fraud velocity counters — sliding window PAN velocity (`fraud:velocity:pan:{hash}:5min`)
- [ ] Distributed lock — `SETNX lock:{key}` TTL 5s, prevents cache stampede

### Cache Hierarchy 🔥
- [ ] L1 Caffeine — JVM-local, microseconds, top 10k BINs
- [ ] L2 Redis — shared across all instances, milliseconds
- [ ] L3 Postgres — source of truth, always correct, ~5ms
- [ ] Cache stampede — many threads miss L2 simultaneously, overwhelm Postgres
- [ ] Prevention: Redis SETNX lock — only one thread fetches from DB

### Cache Invalidation
- [ ] Event-driven invalidation — `merchant.config.updated` Kafka event clears cache
- [ ] TTL expiry — passive invalidation, no active management needed
- [ ] Write-through vs write-around — update cache on write vs only on read miss

---

## 8. Apache Kafka

### Core Concepts 🔥
- [ ] Kafka is a distributed event streaming platform — durable, ordered, replayable
- [ ] **Topic** — named stream of events (like a table for events)
- [ ] **Partition** — topics split into partitions for parallelism
  - `transaction.events` has 12 partitions = 12 consumers can work in parallel
- [ ] **Offset** — position of each message in a partition, starts at 0
- [ ] **Consumer group** — set of consumers sharing work; each partition to one consumer
- [ ] **Retention** — messages kept for 30 days; consumers can replay from any offset

### Producer
- [ ] `acks=all` — all replicas acknowledge before producer considers message sent
- [ ] `enable-idempotence=true` — exactly-once producer semantics (no duplicate sends)
- [ ] `retries=3` — automatic retry on network failure

### Consumer
- [ ] `enable-auto-commit=false` — manual offset commit only 🔥
  - Why: auto-commit can mark a message as processed before processing finishes
  - If processing fails after auto-commit → message is lost
  - Manual commit: only commit after successful processing → retry on failure
- [ ] `auto-offset-reset=earliest` — new consumer starts from beginning of topic
- [ ] `isolation-level=read_committed` — only read messages from committed transactions

### Message Envelope
- [ ] `eventId`, `eventType`, `schemaVersion` — every event has these fields
- [ ] `schemaVersion` — when you add fields, old consumers still work (backward compatible)
- [ ] `aggregateId` — the transaction UUID this event is about
- [ ] `occurredAt` — when the event happened (domain time, not Kafka time)

### Outbox Pattern (connection to DB section)
- [ ] Kafka + DB consistency without distributed transactions (2PC)
- [ ] Save event to `outbox_events` in same DB transaction as business data
- [ ] Relay scheduler reads outbox and publishes to Kafka
- [ ] At-least-once delivery — relay may publish duplicate; consumers must be idempotent

---

## 9. Security Concepts

### PCI-DSS (Payment Card Industry Data Security Standard)
- [ ] PAN (Primary Account Number) — full card number, 16 digits, never stored in plaintext
- [ ] `PanHash.fromRawPan(pan)` — SHA-256 hash, stored instead of PAN
- [ ] PIN block — PIN encrypted immediately at terminal, never in plaintext outside HSM
- [ ] Audit trail — append-only log required by PCI-DSS

### Cryptography 🔥
- [ ] SHA-256 — one-way hash function, 256-bit output, used to hash PANs
- [ ] HMAC-SHA256 — keyed hash, used for webhook signing
  - `HMAC-SHA256(secret, payload_bytes)` → hex digest
  - Header: `X-Payswiff-Signature: sha256={hex}`
- [ ] AES — symmetric encryption (same key to encrypt and decrypt)
- [ ] RSA — asymmetric (public key encrypts, private key decrypts)
- [ ] Constant-time comparison — prevents timing attacks on signature validation
- [ ] PKCS#11 — interface standard for HSM operations (SoftHSM2 implements this)

### EMV (Europay Mastercard Visa) Chip Security
- [ ] ARQC — Authorization Request Cryptogram, generated by chip per transaction
  - Proves the physical card is present (can't be replayed)
- [ ] ARPC — Authorization Response Cryptogram, generated by issuer
  - Card verifies this — proves response is from real issuer
- [ ] ATC — Application Transaction Counter, increments per transaction (prevents replay)
- [ ] IMK — Issuer Master Key, diversified per card to get card-specific session key
- [ ] Key hierarchy: ZMK → ZPK, MAK, DEK → Card Session Key

### PKCS#11 / SoftHSM2
- [ ] HSM (Hardware Security Module) — dedicated crypto hardware, keys never leave
- [ ] SoftHSM2 — software implementation for development (same API, no hardware)
- [ ] `SunPKCS11` — Java's PKCS#11 provider
- [ ] Operations: MAC verify, ARQC verify, PIN block translate, ARPC generate

---

## 10. ISO 8583 Protocol

### What It Is
- [ ] Binary protocol for financial transaction messages (cards, ATMs, POS)
- [ ] Used by every bank, card network (Visa, MC, RuPay), ATM globally
- [ ] jPOS — open-source Java implementation (used in real payment switches)

### Message Structure
- [ ] MTI (Message Type Indicator) — 4 digits, identifies message type
  - `0100` = Authorization Request, `0110` = Authorization Response
  - `0400` = Reversal Request, `0800` = Network Management (heartbeat)
- [ ] Bitmap — 64 or 128 bits, each bit indicates if the corresponding field is present
- [ ] Fields — numbered 1–128, each has defined format (numeric, alphanumeric, binary)
- [ ] LLVAR — variable length field with 2-digit length prefix
- [ ] LLLVAR — variable length field with 3-digit length prefix

### Key Fields
- [ ] Field 2: PAN (card number)
- [ ] Field 4: Amount (12 digits, implied 2 decimal places)
- [ ] Field 11: STAN (System Trace Audit Number) — 6 digit sequence per terminal
- [ ] Field 38: Authorization Code — 6 alphanumeric (in approved response)
- [ ] Field 39: Response Code — `00` = approved, `51` = insufficient funds
- [ ] Field 41: Terminal ID (8 chars)
- [ ] Field 42: Merchant ID (15 chars)
- [ ] Field 52: PIN Data (encrypted PIN block)
- [ ] Field 55: ICC/EMV Data (ARQC, ATC, TVR in TLV format)
- [ ] Field 64: MAC (message authentication code)

### TCP Transport
- [ ] ISO 8583 runs over raw TCP — not HTTP
- [ ] Persistent connections — one connection handles many transactions
- [ ] Why: lower latency (no handshake per message), required at banking TPS
- [ ] Heartbeat every 30s — MTI 0800 echo to detect dead connections

---

## 11. Observability

### Structured Logging 🔥
- [ ] JSON log output — machine-parseable, queryable in CloudWatch/ELK
- [ ] `logstash-logback-encoder` — converts Logback logs to JSON
- [ ] Log levels: ERROR, WARN, INFO, DEBUG — use appropriately
- [ ] **Never log**: PAN, PIN, CVV, card track data — PCI violation

### MDC (Mapped Diagnostic Context) 🔥
- [ ] Thread-local key-value store attached to every log line in current thread
- [ ] `MDC.put("transactionId", txn.id().toString())`
- [ ] Why: trace a transaction across hundreds of log lines without grepping by message
- [ ] `MDC.clear()` in `finally` — thread pool threads reuse, old MDC must be cleared

### Prometheus + Metrics
- [ ] `@Timed`, `Counter`, `Gauge`, `Histogram` — metrics types
- [ ] `payments_authorization_latency_ms` — histogram for p50/p95/p99 latency
- [ ] `payments_transactions_total{status,network}` — counter with labels
- [ ] Scraped by Prometheus every 15s, stored time-series

### Distributed Tracing 🔥
- [ ] Trace — one user request spanning multiple services
- [ ] Span — one unit of work within a trace (e.g., DB query, HSM call)
- [ ] Trace ID propagates via HTTP headers and Kafka message headers
- [ ] OpenTelemetry — vendor-neutral SDK, auto-instruments Spring
- [ ] Jaeger — UI for viewing traces locally
- [ ] CloudWatch X-Ray — AWS equivalent in production

---

## 12. DevOps & CI/CD

### Docker 🔥
- [ ] Container — isolated process with its own filesystem, network, dependencies
- [ ] Image — immutable blueprint for containers (built from Dockerfile)
- [ ] Multi-stage build — builder stage compiles, final stage has only JRE (smaller image)
- [ ] `eclipse-temurin:21-jre-alpine` — slim JRE image (no compiler, no tools)
- [ ] Non-root user — security: `USER payments` in Dockerfile
- [ ] `HEALTHCHECK` — Docker monitors container health
- [ ] `.dockerignore` — exclude files from build context (like `.gitignore`)

### Docker Compose
- [ ] Defines multi-container local environment in one YAML file
- [ ] `depends_on` + `condition: service_healthy` — wait for Postgres to be ready
- [ ] `healthcheck` — command Docker runs to test if container is healthy
- [ ] Volumes — persist data between container restarts (`pgdata:/var/lib/postgresql/data`)
- [ ] Networks — containers talk to each other by service name (`postgres:5432`)

### GitHub Actions (CI/CD) 🔥
- [ ] Workflow — YAML file in `.github/workflows/` triggered by events (push, PR)
- [ ] Job — set of steps running on a runner (ubuntu-latest)
- [ ] Step — individual command or action
- [ ] Matrix strategy — run same job with multiple configurations
- [ ] Secrets — `${{ secrets.AWS_ACCESS_KEY_ID }}` — encrypted, not in code
- [ ] `vars.DEPLOY_ENABLED` — repository variable, not secret (can use in `if` condition)
- [ ] `needs: [test]` — job dependency, ensures test passes before build
- [ ] `environment: prod` with required reviewers — manual approval gate

### Git Workflow
- [ ] Feature branches — `feat/6-domain-models`, `fix/23-reversal-race`
- [ ] Conventional commits — `feat(#6): add TransactionStatus enum`
- [ ] Squash merge — all commits in PR collapsed into one on main
- [ ] `Closes #6` in PR body — auto-closes issue when PR merges
- [ ] Why squash: clean linear history on main, full history preserved in branch

### AWS Services Used
- [ ] ECS Fargate — run containers without managing servers
- [ ] RDS PostgreSQL — managed Postgres
- [ ] ElastiCache Redis — managed Redis
- [ ] S3 — object storage (settlement files, reconciliation reports)
- [ ] ALB — Application Load Balancer, distributes HTTP traffic
- [ ] CloudFront — CDN, serves React SPAs from edge PoPs
- [ ] Secrets Manager — encrypted storage for DB passwords, API keys
- [ ] CloudWatch — logs and metrics
- [ ] IAM — access control (task roles, not hardcoded credentials)
- [ ] ECR — container image registry

---

## 13. Payments Domain Knowledge

### Payment Networks
- [ ] Visa, Mastercard, RuPay (NPCI for domestic cards), UPI (NPCI for UPI)
- [ ] **Acquirer** — your bank/payment processor (Payswiff in this project)
- [ ] **Issuer** — cardholder's bank (who actually has the money)
- [ ] **Network** — Visa/MC/NPCI route between acquirer and issuer
- [ ] BIN (Bank Identification Number) — first 6-8 digits of card, identifies issuer

### Transaction Lifecycle 🔥
- [ ] Authorization — "Is this card valid? Is there enough money?" — hold placed
- [ ] Capture — "Actually take the money" — triggers settlement
- [ ] Dual-message: auth (0100) + separate capture (0200) — hotels, e-commerce
- [ ] Single-message: auth + capture combined — retail, debit cards
- [ ] Settlement — batch process where acquirer collects from networks daily
- [ ] Reconciliation — three-way match: switch log vs network file vs bank statement
- [ ] Payout — net amount sent to merchant's bank account

### Reversal vs Refund
- [ ] **Reversal** — before settlement, cancels authorization hold, instant, MTI 0400
- [ ] **Refund** — after settlement, new transaction, 3-7 business days, MTI 0200
- [ ] Partial reversal — auth ₹10,000, capture ₹8,000, reverse ₹2,000

### Fraud Prevention
- [ ] Velocity checks — PAN used 3 times in 5 minutes → BLOCK
- [ ] Risk levels: LOW, MEDIUM, HIGH, BLOCK
- [ ] BLOCK → decline locally without forwarding upstream (saves network costs)
- [ ] Field 44 — fraud flag passed to issuer for HIGH risk
- [ ] Idempotency — same STAN+terminal+date → return cached response, never reprocess

### Chargeback
- [ ] Customer disputes a transaction through their bank
- [ ] Merchant has 30-45 days to submit evidence
- [ ] Evidence: ARQC verified, PIN verified, EMV chip confirmed = merchant wins
- [ ] Reserve account — merchant reserve covers chargeback debits instantly

### MDR (Merchant Discount Rate)
- [ ] Fee the merchant pays on each transaction
- [ ] Waterfall: gross → interchange (issuer's cut) → network fee → MDR (Payswiff) → net to merchant
- [ ] Reserve withholding — 5% held back, released after settlement cycle

---

## 14. React Frontend (Week 6)

### Core React
- [ ] Components — reusable UI building blocks
- [ ] Props — data passed from parent to child components
- [ ] State — data that changes and causes re-render
- [ ] Hooks — functions starting with `use` that add state/effects to function components
- [ ] `useEffect` — run side effects (fetch data, subscribe to events, timers)
- [ ] JSX — HTML-like syntax in JavaScript files

### State Management
- [ ] React Query (TanStack Query) — server state management
  - Cache, background refetch, stale-while-revalidate
  - `useQuery()`, `useMutation()`, `queryClient.invalidateQueries()`
- [ ] Zustand — lightweight global client state
  - Not for server data — only UI state (filters, theme, notifications)
- [ ] Rule: never put server data in Zustand, never put UI state in React Query

### Advanced React Patterns
- [ ] Optimistic updates — flip state in UI immediately, roll back if server rejects
- [ ] Virtual scrolling — render only visible rows; `@tanstack/virtual`
- [ ] Loading skeletons — placeholder layout while data loads (no spinners)
- [ ] Error boundaries — catch component crashes, show fallback
- [ ] Code splitting — `React.lazy` + `Suspense`, load only what's needed
- [ ] `startTransition` — mark low-priority state updates (React 18 Concurrent)
- [ ] `useDeferredValue` — show stale results while computing new ones

### Real-time
- [ ] SSE (Server-Sent Events) — one-way server → client stream over HTTP
  - Used for: live transaction feed, QR status updates
  - `new EventSource('/sse/transactions')` — native browser API
- [ ] Why SSE over WebSocket: simpler, HTTP, auto-reconnect, sufficient for one-way data

### Form Handling
- [ ] `react-hook-form` — efficient form state management
- [ ] `zod` — schema validation library
- [ ] `zodResolver` — bridges zod schema to react-hook-form
- [ ] Client-side Luhn check before sending to server

---

## 15. Concepts to Learn in Order (Suggested Path)

### Week 1 (Java Foundations)
- [ ] Java records and compact constructors
- [ ] BigDecimal arithmetic for money
- [ ] Enums with methods
- [ ] JUnit 5 + AssertJ — write your first test
- [ ] TDD red-green-refactor on a simple value object

### Week 2 (Architecture)
- [ ] Hexagonal architecture — draw the three layers on paper
- [ ] Ports and adapters — trace one request from controller to DB
- [ ] Constructor injection vs field injection — write both, see the difference
- [ ] Repository pattern — how domain record becomes JPA entity and back

### Week 3 (Spring Boot)
- [ ] Run a Spring Boot app, hit an endpoint
- [ ] `@Transactional` — what commits and what rolls back
- [ ] Spring profiles — different `application-{profile}.yml` per environment
- [ ] `@ConditionalOnProperty` — swap implementations via config

### Week 4 (Infrastructure)
- [ ] Docker — build an image, run it, see logs
- [ ] Docker Compose — start Postgres + Redis + your app together
- [ ] Kafka — produce a message, consume it, understand offsets
- [ ] Redis — set a key with TTL, watch it expire

### Week 5 (Advanced Patterns)
- [ ] Transactional outbox — trace the save → relay → Kafka → consume flow
- [ ] Distributed tracing — look at a trace in Jaeger spanning two services
- [ ] Optimistic locking — trigger an `OptimisticLockException` deliberately
- [ ] State machine — map all 20 `TransactionStatus` transitions on paper

### Week 6 (Payments Domain)
- [ ] Read the ISO 8583 Wikipedia article
- [ ] Trace a card transaction from terminal to approved response step by step
- [ ] Understand ARQC/ARPC — why chip is safer than magnetic stripe
- [ ] Read about PCI-DSS compliance requirements

### Week 7 (React & AWS)
- [ ] Build a small React app with React Query fetching from a REST endpoint
- [ ] Add SSE — stream events and display in a list
- [ ] Deploy a Docker container on AWS ECS manually (not via CI)
- [ ] Set up a CloudWatch log group and tail it

---

*This checklist grows as new tickets are implemented. Updated when new concepts are introduced.*
*Last updated: May 2026 — after ticket #5 (domain value objects)*
