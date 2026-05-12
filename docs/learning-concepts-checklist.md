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
- [ ] Debezium replaces the relay scheduler (post-June): reads Postgres WAL instead of polling

### Optimistic Locking 🔥
- [ ] `@Version Long version` on JPA entity
- [ ] Two concurrent updates: first wins, second gets `OptimisticLockException`
- [ ] No DB locks held → high throughput, no deadlocks

### Value Objects / No Primitive Obsession 🔥
- [ ] Wrap primitives in self-validating types: `MerchantId`, `Money`, `PanHash`
- [ ] Why: compiler catches `(terminalId, merchantId)` argument swap; String can't
- [ ] Validation at construction — invalid value never exists in the system

### Event Sourcing 🔥
- [ ] Store events (what happened) instead of current state (snapshots of what is)
- [ ] `transaction_events` table is the source of truth, not the `transactions` table
- [ ] Replay all events → reconstruct current state at any point in time
- [ ] Benefits: full audit trail, time-travel debugging, easy read model rebuilding
- [ ] Difference from outbox: outbox is for publishing; event store is for persistence

### Full CQRS (Event-Sourced Read Models) 🔥
- [ ] Write side: command → domain service → event stored (event-sourced)
- [ ] Read side: Debezium reads events from WAL → Kafka → multiple specialized consumers
  - Consumer 1: updates `transaction_dashboard` read model (latest status, amounts)
  - Consumer 2: updates `fraud_analytics` read model (velocity, patterns)
  - Consumer 3: updates `settlement_queue` read model (captured, pending)
- [ ] Read models are denormalized, optimized for their query patterns (no JOINs)
- [ ] Eventually consistent: read models lag behind write side by milliseconds
- [ ] Why: write side optimized for throughput; read side optimized for each query

### CAP Theorem 🔥
- [ ] A distributed system can guarantee at most 2 of 3: Consistency, Availability, Partition Tolerance
- [ ] Partition tolerance is non-negotiable (networks fail) — real choice is C vs A
- [ ] **CP (Consistency + Partition)**: choose consistency over availability on partition
  - Used here: Postgres (transactions) — never serve stale data on a payment
- [ ] **AP (Availability + Partition)**: choose availability over consistency on partition
  - Used here: Redis (BIN cache, fraud velocity) — slightly stale cache acceptable
- [ ] **CA (Consistency + Availability)**: only works in a single-node system — no real distributed systems
- [ ] Interview framing: "For financial transactions we chose CP; for fraud velocity we chose AP"

### Saga Pattern 🔥
- [ ] Manage distributed transactions across multiple services without 2PC
- [ ] **Choreography**: services emit events, others react — loose coupling
  - Used here: payment.authorized → webhook-dispatcher consumes → delivers → commits
- [ ] **Orchestration**: central coordinator tells each service what to do (Temporal)
  - Used here: settlement saga — Temporal workflow calls steps in order, retries failures
- [ ] Compensating transactions: if step N fails, undo steps 1..N-1

### Consistent Hashing 🔥
- [ ] Distribute work across N nodes so adding/removing a node only remaps `1/N` keys
- [ ] Used in: Redis Cluster, Kafka partition assignment, load balancer sticky sessions
- [ ] Alternative to modulo hashing (which remaps all keys when N changes)
- [ ] Virtual nodes — each physical node occupies multiple positions on the ring, improving balance

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

### jOOQ — Type-Safe SQL 🔥
- [ ] What: generates Java classes from your DB schema, write SQL with type-safety
- [ ] Why over Spring Data JPA: complex JOINs, aggregations, window functions are cleaner
- [ ] Rule in this project: JPA for simple CRUD; jOOQ for reports and analytics
- [ ] `DSLContext.select().from(TRANSACTIONS).where(...).fetch()` — type-safe at compile time
- [ ] Code generation: jOOQ reads Postgres schema → generates `TRANSACTIONS` constant class
- [ ] `sum(TRANSACTIONS.AMOUNT)` — SQL aggregation with Java compile-time checking
- [ ] `Result<Record>` → map to domain objects manually (no magic ORM mapping)

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
- [ ] Post-June: Temporal replaces Spring Batch for saga-style distributed workflows

### Spring Actuator
- [ ] `/actuator/health` — reports DB, Redis, Kafka, custom indicators
- [ ] Liveness vs Readiness probes — Kubernetes/ECS restart vs ALB routing
- [ ] `HealthIndicator` interface — implement for custom checks (HSM, network switch)
- [ ] Prometheus metrics endpoint — `/actuator/prometheus`

### Spring Security
- [ ] Filter chain — requests pass through ordered filters
- [ ] `ApiKeyAuthFilter` — validates `X-Api-Key` header on acquiring REST API
- [ ] mTLS (mutual TLS) — both client and server present certificates

### Spring for GraphQL
- [ ] `@QueryMapping` — maps a GraphQL query to a method
- [ ] `@MutationMapping` — maps a GraphQL mutation
- [ ] `@SubscriptionMapping` — maps a GraphQL subscription (real-time via WebSocket)
- [ ] DataLoader — batch N resolver calls into one query (prevents N+1 in GraphQL)
- [ ] Schema-first: define `.graphqls` schema file, then implement resolvers

### OpenFeature + Unleash (Feature Flags)
- [ ] Feature flags: toggle features on/off without deploying new code
- [ ] OpenFeature: vendor-neutral SDK (`isEnabled("new-fraud-model", context)`)
- [ ] Unleash: self-hosted feature flag server (strategy: gradual rollout, A/B test)
- [ ] Used for: enabling ML fraud scoring for 5% of traffic initially
- [ ] `@ConditionalOnFeatureFlag` alternative: runtime check in service layer

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

### Pact — Consumer-Driven Contract Testing 🔥
- [ ] Problem: integration tests are slow; unit tests don't catch contract breaks between services
- [ ] Solution: consumer defines what it expects; provider verifies it satisfies those expectations
- [ ] Consumer (acquiring-service) writes Pact → publishes to Pact Broker
- [ ] Provider (payment-switch) reads Pact from Broker → verifies it still passes
- [ ] Break the contract → CI fails immediately, without a full integration test run
- [ ] Used for: terminal↔acquiring, acquiring↔switch, dispatcher↔merchant contracts

### PIT Mutation Testing 🔥
- [ ] Mutation testing: inject small code changes ("mutants") into production code
- [ ] Run your tests against each mutant — if a test fails, the mutant is "killed"
- [ ] Surviving mutants = tests that didn't catch a real bug
- [ ] Stronger than coverage: 90% coverage can still miss logic errors
- [ ] `mvn org.pitest:pitest-maven:mutationCoverage` — generates HTML report
- [ ] Used for: domain service unit tests (state machine, fraud engine, fee waterfall)

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

### PgBouncer — Connection Pooling
- [ ] Problem: Postgres handles ~100 connections; ECS with 10 instances × 20 pool = 200 connections
- [ ] Solution: PgBouncer sits between app and Postgres, multiplexes connections
- [ ] **Transaction mode**: connection returned to pool after each transaction (most efficient)
- [ ] **Session mode**: connection held for lifetime of app connection (some features need this)
- [ ] Config: `pool_size=25`, `max_client_conn=200` — app sees 200 connections, Postgres sees 25
- [ ] Used for: all services in K8s deployment (post-June)

### Elasticsearch 🔥
- [ ] Distributed search engine — full-text search, aggregations, analytics
- [ ] **Index** — collection of documents (like a DB table but schema-flexible)
- [ ] **Document** — JSON record stored in an index (like a DB row)
- [ ] **Inverted index** — word → list of documents containing it (enables fast text search)
- [ ] Used for: transaction search (`merchant_id`, `pan_hash`, `status`, full-text `notes`)
- [ ] Kibana — UI to query and visualize Elasticsearch data
- [ ] Spring Data Elasticsearch — `@Document`, `ElasticsearchRepository`
- [ ] Sync strategy: Debezium → Kafka → Elasticsearch consumer (eventual consistency)

### pgvector — Vector Embeddings 🔥
- [ ] Extension to Postgres that adds a `vector` column type
- [ ] Stores high-dimensional float arrays (e.g., text embeddings — 1536 floats for Cohere)
- [ ] Used for: storing transaction context embeddings for fraud similarity search
- [ ] **HNSW index** — Hierarchical Navigable Small World — approximate nearest neighbour, fast
  - `CREATE INDEX ON transactions USING hnsw (embedding vector_cosine_ops)`
- [ ] Query: `SELECT * FROM transactions ORDER BY embedding <=> $1 LIMIT 10`
  - `<=>` is cosine distance; `<->` is L2 distance
- [ ] Why Postgres (not Pinecone): one less infrastructure piece; good enough at < 10M vectors

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

### Redis Sentinel — High Availability
- [ ] Sentinel monitors master + replicas; promotes replica on master failure
- [ ] **3-node Sentinel** (minimum for quorum): 1 master + 1 replica + 1 pure sentinel
- [ ] Auto-failover: Sentinel detects master down (after `down-after-milliseconds`) → promotes replica
- [ ] Spring connects to Sentinel: `spring.data.redis.sentinel.master: mymaster`
- [ ] RTO (Recovery Time Objective) for Redis: ~30 seconds (Sentinel promotion time)
- [ ] Alternative: Redis Cluster (sharding + HA) — overkill for this project's data size

### Bloom Filter — Fast Idempotency 🔥
- [ ] Probabilistic data structure: tests membership in O(1) with configurable false positive rate
- [ ] "Definitely NOT in set" or "PROBABLY in set" — never false negatives
- [ ] Used for: idempotency fast-path — bloom filter check before Redis → before Postgres UNIQUE
- [ ] `BF.ADD payments:seen:idempotency {key}` / `BF.EXISTS payments:seen:idempotency {key}`
- [ ] Config: 10M capacity, 0.001% false positive rate → ~18MB memory
- [ ] False positive means: occasionally re-check Redis/DB — not a correctness issue
- [ ] Layered idempotency: Bloom (O(1) memory) → Redis (O(1) network) → DB UNIQUE (guaranteed)

### Semantic Caching (Post-June AI)
- [ ] Cache LLM responses by semantic similarity, not exact prompt match
- [ ] Store `(embedding, response)` pairs in Redis with cosine similarity index
- [ ] On new query: compute embedding → search Redis → if similarity > 0.95 → return cached
- [ ] Why: identical fraud queries phrased differently still return same result (saves LLM tokens)
- [ ] Implementation: `redis-py` + `RedisVL` library for vector search

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

### Schema Registry + Avro 🔥
- [ ] Problem: without schema enforcement, a producer can break all consumers silently
- [ ] Schema Registry: central store for message schemas; producers/consumers validate against it
- [ ] Avro: binary serialization format (compact, fast) with a schema stored separately
- [ ] Schema evolution rules: BACKWARD (new schema reads old data), FORWARD (old schema reads new data)
- [ ] `@Schema` → generate Java classes from `.avsc` files → compile-time type safety
- [ ] Confluent Schema Registry: `POST /subjects/{topic}-value/versions` to register schema
- [ ] Wire format: `[magic byte][schema ID (4 bytes)][avro bytes]` — consumer looks up schema by ID

### Debezium CDC (Change Data Capture) 🔥
- [ ] Problem: outbox relay scheduler polls DB every 100ms — adds latency and DB load
- [ ] Solution: Debezium reads Postgres WAL (Write-Ahead Log) and streams changes to Kafka
- [ ] **WAL** — Postgres's internal append-only log of every change; Debezium is a replication client
- [ ] Zero application code changes — Debezium reads DB, not your app
- [ ] Latency: milliseconds (vs 100ms+ polling)
- [ ] Debezium event: `{op: "c", before: null, after: {id: ..., status: "AUTHORIZED", ...}}`
- [ ] Used for: outbox relay (replaces scheduler), feeding Elasticsearch sync, feeding read models
- [ ] Kafka connector config: `"connector.class": "io.debezium.connector.postgresql.PostgresConnector"`
- [ ] `publication.autocreate.mode: filtered` — only capture `outbox_events` table

### Outbox Pattern (connection to DB section)
- [ ] Kafka + DB consistency without distributed transactions (2PC)
- [ ] Save event to `outbox_events` in same DB transaction as business data
- [ ] Relay scheduler reads outbox and publishes to Kafka
- [ ] At-least-once delivery — relay may publish duplicate; consumers must be idempotent

### Backpressure — Consumer Pause/Resume
- [ ] Problem: producer floods Kafka faster than consumer can process (e.g. during fraud ML slowdown)
- [ ] Solution: consumer pauses partition consumption until processing catches up
- [ ] `consumer.pause(partitions)` — stop fetching, but keep heartbeat alive (avoids rebalance)
- [ ] `consumer.resume(partitions)` — resume when queue drains below threshold
- [ ] Used for: webhook-dispatcher under load, fraud-scoring Kafka consumer

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
- [ ] **Grafana Tempo** — distributed trace storage and visualization (replaces Jaeger in v2.0)
- [ ] Why Tempo over Jaeger: integrates natively with LGTM stack (same Grafana instance)

### Grafana LGTM Stack 🔥
- [ ] **Loki** — log aggregation (like ELK but cheaper and Grafana-native)
  - Logs are indexed by labels only (not full-text), making storage cheap
  - LogQL: `{service="acquiring-service"} |= "ERROR"`
- [ ] **Grafana** — unified visualization for metrics (Mimir), logs (Loki), traces (Tempo)
  - Single pane of glass: click a Prometheus alert → jump to related logs → jump to trace
- [ ] **Tempo** — distributed trace storage, TraceQL queries
- [ ] **Mimir** — horizontally scalable Prometheus (replaces Prometheus for long-term storage)
- [ ] **OTel Collector** — receives OTLP telemetry, fans out to Loki + Tempo + Mimir
  - Apps send to one endpoint; Collector routes to the right backend
  - Decouples app from observability backend — swap Grafana for Datadog without app changes
- [ ] Correlation: trace ID in log line → click in Loki → jump to Tempo trace

### Sentry — Error Tracking
- [ ] Captures exceptions with full stack trace, request context, user info
- [ ] `Sentry.captureException(e)` — manually report an exception
- [ ] Performance monitoring: tracks transaction (HTTP request) latency in Sentry
- [ ] Release tracking: tag deployments so you can see "this error started in v0.3.0"
- [ ] Alerts: "new error occurred", "error rate increased 10x in 5 minutes"
- [ ] Integration: GitHub Issues auto-created from Sentry alerts

### SLOs, SLAs, Error Budgets 🔥
- [ ] **SLI** (Service Level Indicator) — the metric you measure (e.g., success rate %)
- [ ] **SLO** (Service Level Objective) — your internal target (e.g., 99.9% success rate)
- [ ] **SLA** (Service Level Agreement) — contractual promise to customers (stricter than SLO)
- [ ] **Error budget** — how much failure is allowed: `1 - SLO = 0.1% = 43.8 min/month`
- [ ] **Burn rate** — how fast you're consuming error budget; alerting on burn rate
  - Fast burn alert: 14x budget consumption in 1 hour → wake-up page
  - Slow burn alert: 1x budget consumption over 6 hours → ticket
- [ ] This project's SLOs: auth success ≥ 99.9%, p95 latency < 500ms, webhook delivery ≥ 99.5%

### DORA Metrics 🔥
- [ ] **Deployment Frequency**: how often you deploy to prod (elite: multiple/day)
- [ ] **Lead Time for Changes**: time from commit to prod (elite: < 1 hour)
- [ ] **MTTR** (Mean Time to Restore): time to recover from incident (elite: < 1 hour)
- [ ] **Change Failure Rate**: % of deployments causing incident (elite: < 5%)
- [ ] Measured via GitHub Actions deploy events + PagerDuty/alertmanager incident events

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
- [ ] 6-job pipeline: test → integration-test → build-push → deploy-dev → deploy-qa → deploy-prod

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

## 14. TypeScript + Next.js Frontend

### TypeScript Fundamentals 🔥
- [ ] TypeScript is a typed superset of JavaScript — compiles to JS, catches errors at build time
- [ ] **Why TypeScript over JavaScript**: catch type mismatches at compile time, not at runtime
- [ ] Strict mode — `"strict": true` in `tsconfig.json` — enables all strict checks
- [ ] Basic types: `string`, `number`, `boolean`, `null`, `undefined`, `void`, `never`
- [ ] `interface` vs `type` — interfaces are extendable, types are more powerful (unions, intersections)
- [ ] Generics — `function identity<T>(arg: T): T` — type-safe code that works with multiple types
- [ ] `Record<K, V>` — typed object with known key and value types
- [ ] `Partial<T>` — makes all properties optional (useful for update DTOs)
- [ ] `Readonly<T>` — makes all properties readonly (domain model equivalent)
- [ ] `z.infer<typeof schema>` — infer TypeScript type from Zod schema (single source of truth)
- [ ] Non-null assertion `!` and optional chaining `?.` — use `?.` always, `!` rarely
- [ ] Discriminated unions — `type Result = { ok: true; data: T } | { ok: false; error: string }`

### Next.js App Router 🔥
- [ ] **App Router** (Next.js 13+) vs Pages Router (legacy)
  - `app/` directory vs `pages/` directory
  - App Router uses React Server Components by default
- [ ] **Server Components** (default) — rendered on server, zero JS sent to client
  - Can `async/await` directly, access DB, call APIs — no useEffect needed
  - Not interactive — no `useState`, `useEffect`, event handlers
- [ ] **Client Components** — `'use client'` directive, rendered on client
  - Use for: interactivity, browser APIs, event handlers, state
- [ ] **When to use Server vs Client**: server for data fetching + initial render; client for interactivity
- [ ] Layouts — `layout.tsx` wraps child routes, persists across navigation (sidebar, header)
- [ ] `loading.tsx` — automatic Suspense boundary for async Server Components (streaming)
- [ ] `error.tsx` — automatic error boundary per route segment
- [ ] API Routes (BFF) — `app/api/*/route.ts` → `GET`, `POST` handlers as BFF endpoints
- [ ] `generateStaticParams` — SSG for known params; `revalidate` — ISR (refresh every N seconds)

### Next.js Data Fetching
- [ ] **SSR** (Server-Side Rendering) — fetch on each request, always fresh
  - `fetch(url, { cache: 'no-store' })` in Server Component
- [ ] **SSG** (Static Site Generation) — generate HTML at build time
  - `fetch(url)` — default is `force-cache`; `revalidate: 3600` for ISR
- [ ] **ISR** (Incremental Static Regeneration) — background refresh after `revalidate` seconds
- [ ] **Streaming** — stream partial HTML with `<Suspense>` boundaries
  - Sends page shell immediately; fills in data chunks as they resolve
- [ ] React Query for client-side data (dashboard live feed, QR status polling)

### State Management (TypeScript Style)
- [ ] React Query v5 — server state, fully typed with generics
  - `useQuery<TransactionSummary[]>({ queryKey: ['transactions'] })`
- [ ] Zustand — global UI state, typed store
  - `const useStore = create<AppState>()((set) => ({ ... }))`
- [ ] URL state with **nuqs** — sync filter state to URL query params
  - `const [status, setStatus] = useQueryState('status')` — shareable filtered URLs
- [ ] Form state: `react-hook-form` + `zod` + `zodResolver`

### Advanced React Patterns (TypeScript)
- [ ] Optimistic updates — typed `onMutate` context for rollback
- [ ] Virtual scrolling — `@tanstack/virtual` with typed row data
- [ ] Loading skeletons — `isLoading` branch renders typed skeleton component
- [ ] Error boundaries — `ErrorBoundary` with typed `fallback` prop
- [ ] Code splitting — `React.lazy(() => import('./pages/Simulator'))` + `Suspense`
- [ ] `startTransition` — mark low-priority state updates (filter changes)
- [ ] `useDeferredValue` — show stale results while computing new ones

### shadcn/ui
- [ ] NOT a component library you install — it's a collection of copy-paste components
- [ ] Built on **Radix UI** primitives (accessible, unstyled) + Tailwind CSS
- [ ] Run `npx shadcn@latest add button` → copies component source into `components/ui/`
- [ ] You own the code — customise freely without fighting with a third-party API
- [ ] Key components used: `Button`, `Badge`, `DataTable`, `Sheet` (drawer), `Command` (cmdk)

### UX Libraries
- [ ] **sonner** — toast notifications (`toast.success("Payment authorized")`)
- [ ] **cmdk** — command palette (`⌘K` opens searchable list of actions)
  - `<Command.Dialog>` with `<Command.Input>`, `<Command.List>`, `<Command.Item>`
- [ ] **nuqs** — URL search param state management (filters survive page refresh)
- [ ] **next/image** — optimized `<Image>` with lazy loading, WebP conversion, size optimization
- [ ] **next/font** — optimizes web fonts, eliminates layout shift, self-hosted

### WebSockets (STOMP)
- [ ] WebSocket: bidirectional, persistent TCP connection over HTTP upgrade
- [ ] STOMP: messaging protocol on top of WebSocket (publish/subscribe to topics)
- [ ] Why over SSE: bidirectional (client can send commands back to server)
- [ ] Spring WebSocket: `@MessageMapping("/trigger")` handles client messages
- [ ] Client: `new Client({ brokerURL: 'ws://...' })` → `client.subscribe('/topic/events', handler)`
- [ ] Used for: ISO 8583 event log (bidirectional: trigger scenario + receive events)

### Playwright — E2E Testing 🔥
- [ ] Launches a real browser (Chromium, Firefox, WebKit) and interacts with your app
- [ ] `test('user can pay', async ({ page }) => { await page.goto('/simulator'); ... })`
- [ ] `page.getByRole('button', { name: 'Pay Now' }).click()` — accessible selectors
- [ ] `expect(page.locator('.event-log')).toContainText('AUTHORIZED')` — assertions
- [ ] `page.waitForResponse('**/api/transactions')` — wait for network request
- [ ] Why over Cypress: faster, supports multiple browsers, first-class TypeScript
- [ ] Run with: `npx playwright test` — runs headless in CI, headed locally

### next-pwa (Progressive Web App)
- [ ] Service Worker: background JS that intercepts network requests and can cache responses
- [ ] `next-pwa` wraps Next.js with Workbox to generate a service worker
- [ ] **Offline support**: cache assets + API responses; show cached data when offline
- [ ] Install prompt: users can "Add to home screen" → app icon, no browser chrome
- [ ] Push notifications (via Web Push API): backend sends push to browser even when tab is closed

### Storybook + Chromatic
- [ ] Storybook: develop and document UI components in isolation, without the full app
- [ ] Story: `export const Default: Story = { args: { status: 'AUTHORIZED' } }`
- [ ] Why: visual test components in every state; share with design; catch regressions
- [ ] Chromatic: CI service that takes screenshots of every Storybook story
  - On PR: compare screenshots vs baseline → flag visual regressions
  - `npx chromatic --project-token=...` in CI

### PostHog — Product Analytics
- [ ] Self-hostable product analytics (events, funnels, session recordings)
- [ ] `posthog.capture('payment_initiated', { method: 'CARD', network: 'VISA' })`
- [ ] Used for: track which demo scenarios users run, which features are used most
- [ ] Session recording: watch real user interactions (anonymized) to find UX friction

---

## 15. gRPC + Protocol Buffers

### Why gRPC 🔥
- [ ] HTTP/2 multiplexing — many requests on one connection (vs HTTP/1.1's one request/connection)
- [ ] Binary (Protobuf) instead of JSON — 5-10x smaller, 2-3x faster to parse
- [ ] Strongly typed contracts (`.proto` files) — compiler catches mismatches across services
- [ ] Streaming: unary, server-stream, client-stream, bidirectional
- [ ] Used for: payment-switch ↔ fraud-scoring-service (low latency, streaming capability)

### Protocol Buffers
- [ ] Define messages and services in `.proto` files
  ```proto
  message FraudRequest {
    string pan_hash = 1;
    int64 amount_paise = 2;   // INR paise, never float
    string network = 3;
  }
  service FraudScoringService {
    rpc Score (FraudRequest) returns (FraudResponse);
    rpc ScoreStream (stream FraudRequest) returns (stream FraudResponse);
  }
  ```
- [ ] `protoc` compiler generates Java classes from `.proto` — import and use directly
- [ ] Field numbers (1, 2, 3) must never change — adding new fields is backward compatible
- [ ] `int64` for money in smallest unit (paise) — no float/double ever

### gRPC in Spring Boot
- [ ] `grpc-spring-boot-starter` — auto-configures gRPC server on port 9090
- [ ] `@GrpcService` on your service implementation class
- [ ] `@GrpcClient("fraud-scoring-service")` — inject client stub
- [ ] Interceptors — for auth, logging, tracing (equivalent to HTTP filters)
- [ ] `ManagedChannel` — the connection to remote gRPC service (pooled, reused)

### gRPC Streaming
- [ ] **Unary**: one request, one response (like REST POST)
- [ ] **Server streaming**: one request, many responses streamed back
  - Used for: streaming fraud reasoning tokens back from LLM fraud agent
- [ ] **Bidirectional streaming**: continuous exchange of messages on one connection
  - Used for: batch fraud scoring (send N transactions, receive N scores concurrently)

---

## 16. GraphQL

### Core Concepts 🔥
- [ ] Client asks for exactly the data it needs — no over-fetching (REST: you get all fields)
- [ ] Strongly typed schema — `type Transaction { id: ID!, amount: Float!, status: Status! }`
- [ ] **Query** — read data (like GET)
- [ ] **Mutation** — change data (like POST/PUT)
- [ ] **Subscription** — real-time updates over WebSocket
- [ ] Single endpoint: `POST /graphql` with `{ query: "...", variables: {} }`

### GraphQL Schema
- [ ] Schema-first approach: write `.graphqls` → Spring generates resolver stubs
- [ ] Types: scalar (String, Int, Float, Boolean, ID), object, enum, input, interface
- [ ] `!` means non-null: `amount: Float!` — never null; `amount: Float` — can be null
- [ ] Connections pattern for pagination: `transactions(first: 10, after: cursor): TransactionConnection`

### Spring for GraphQL
- [ ] `@QueryMapping` — `@QueryMapping getTransaction(UUID id)` maps to `query { getTransaction(id) }`
- [ ] `@MutationMapping` — maps to GraphQL mutation
- [ ] `@SubscriptionMapping` — returns `Publisher<T>` (Reactor), streams via WebSocket
- [ ] `@SchemaMapping` — resolve a field on a type (e.g., merchant name from merchant ID)
- [ ] DataLoader — batch and cache N resolver calls into one DB query (solves N+1 in GraphQL)
  - `@BatchMapping` in Spring for GraphQL — automatically creates DataLoader

### When to Use GraphQL vs REST vs gRPC
- [ ] **GraphQL**: client-driven queries, flexible data requirements (dashboard with many widgets)
- [ ] **REST**: simple CRUD, public APIs, caching is important (HTTP cache headers)
- [ ] **gRPC**: internal service-to-service, performance-critical, streaming needed

---

## 17. Resilience4j — Fault Tolerance

### Why Fault Tolerance 🔥
- [ ] In distributed systems, failures are normal — network timeouts, service crashes
- [ ] Without protection: one slow downstream cascades into full system failure
- [ ] Resilience4j: library of fault tolerance patterns for Java (replaces Hystrix)

### Circuit Breaker 🔥
- [ ] States: CLOSED (normal) → OPEN (failing) → HALF_OPEN (testing recovery)
- [ ] CLOSED: all requests pass through; count failures
- [ ] OPEN: immediately reject requests with fallback (fast fail, no waiting)
- [ ] HALF_OPEN: allow N test requests; if they succeed → CLOSED; if fail → OPEN
- [ ] Config: `failureRateThreshold=50%`, `waitDurationInOpenState=30s`, `slidingWindowSize=10`
- [ ] Used for: upstream network calls (Visa/MC), HSM calls, external webhook delivery
- [ ] `@CircuitBreaker(name = "visaNetwork", fallbackMethod = "fallbackRoute")`

### Bulkhead 🔥
- [ ] Isolate resources so one slow consumer doesn't starve others
- [ ] **Semaphore bulkhead**: limit concurrent calls to N (rejects when at limit)
- [ ] **Thread pool bulkhead**: separate thread pool per service (isolation, but overhead)
- [ ] Used for: each payment network gets its own semaphore — Visa slowdown doesn't affect NPCI
- [ ] Config: `maxConcurrentCalls=10`, `maxWaitDuration=100ms`

### Rate Limiter
- [ ] Limit requests per time window to protect downstream service
- [ ] `@RateLimiter(name = "upstreamNetwork")` — throws `RequestNotPermitted` if exceeded
- [ ] Config: `limitForPeriod=100`, `limitRefreshPeriod=1s`, `timeoutDuration=0s`
- [ ] Different from fraud velocity: fraud is about detecting suspicious patterns; rate limiter is about protecting capacity

### Time Limiter
- [ ] Wraps `CompletableFuture` with a timeout that cancels the future if exceeded
- [ ] `@TimeLimiter(name = "hsmOperation", fallbackMethod = "hsmTimeout")`
- [ ] Config: `timeoutDuration=500ms`
- [ ] Used for: HSM calls (500ms), upstream network (15s), webhook delivery (5s)

### Retry
- [ ] Auto-retry on transient failures (network blip, 503, timeout)
- [ ] `@Retry(name = "visaNetwork", fallbackMethod = "onNetworkFailure")`
- [ ] Config: `maxAttempts=3`, `waitDuration=500ms`, `retryExceptions=[IOException.class]`
- [ ] Exponential backoff: `enableExponentialBackoff=true`, `exponentialBackoffMultiplier=2`
- [ ] Important: only retry idempotent operations — never retry payment capture twice

### Load Shedding 🔥
- [ ] When overloaded, reject low-priority work to protect high-priority work
- [ ] Strategy: accept new authorization requests; reject non-critical reporting queries
- [ ] Implementation: check queue depth / active requests before accepting new work
  ```java
  if (activeRequests.get() > LOAD_SHED_THRESHOLD * 0.80) {
      throw new ServiceOverloadedException("Load shedding: try later");
  }
  ```
- [ ] Returns `HTTP 503 Service Unavailable` with `Retry-After` header
- [ ] Combined with Resilience4j RateLimiter + Bulkhead for layered protection

---

## 18. Temporal — Workflow Orchestration

### What Temporal Is 🔥
- [ ] Durable execution engine: workflows survive server crashes, restarts, and network failures
- [ ] Replaces: Spring Batch jobs, hand-rolled sagas, state machines stored in DB
- [ ] Key insight: write normal Java code, Temporal replays it on restart automatically
- [ ] Backed by a workflow history — every step is recorded; failure = replay from last checkpoint

### Core Concepts
- [ ] **Workflow** — defines the overall flow (orchestrator); durable, long-lived
  ```java
  @WorkflowInterface
  public interface SettlementWorkflow {
      @WorkflowMethod
      void settleTransactions(LocalDate date);
  }
  ```
- [ ] **Activity** — actual work (DB reads, HTTP calls, Kafka publish); not durable
  ```java
  @ActivityInterface
  public interface SettlementActivity {
      List<Transaction> aggregateTransactions(LocalDate date);
      void generateFile(List<Transaction> txns, String network);
      void submitToNetwork(String fileKey);
  }
  ```
- [ ] **Worker** — polls Temporal server for tasks and executes workflow/activity code
- [ ] **Signal** — send an event to a running workflow (e.g., "evidence submitted" → chargeback workflow)
- [ ] **Query** — read workflow state without executing (e.g., "what step is this settlement on?")

### Why Temporal over Spring Batch
- [ ] Spring Batch: state stored in `BATCH_JOB_EXECUTION` table — complex restart logic
- [ ] Temporal: state stored in workflow history — restart is automatic, no custom code
- [ ] Temporal handles: retries, timeouts, error handling, compensation — all with normal Java
- [ ] Settlement as Temporal workflow: if step 3 (network submission) crashes → auto-retry from step 3

### Saga with Temporal
- [ ] Long-running transactions across services — Temporal orchestrates compensations
  ```java
  try {
      debitMerchantReserve(chargebackId);    // activity 1
      updateTransactionStatus(txnId);        // activity 2
      notifyMerchant(merchantId);            // activity 3
  } catch (Exception e) {
      creditMerchantReserve(chargebackId);   // compensate activity 1
  }
  ```
- [ ] If any step fails: Temporal runs compensation activities in reverse order

---

## 19. Kubernetes + Helm

### Kubernetes Fundamentals 🔥
- [ ] **Pod** — smallest unit; one or more containers that share network and storage
- [ ] **Deployment** — manages N replicas of a pod; handles rolling updates and rollback
- [ ] **Service** — stable DNS name + load balancing across all pods of a Deployment
- [ ] **Ingress** — HTTP routing from external traffic to internal Services
- [ ] **ConfigMap** — non-secret configuration as key-value pairs
- [ ] **Secret** — base64-encoded sensitive values (DB password, API keys)
- [ ] **Namespace** — logical isolation within a cluster (e.g., `payments-dev`, `payments-prod`)

### Workload Resources
- [ ] **HPA** (Horizontal Pod Autoscaler) — scale pods based on CPU/memory/custom metrics
  - `targetCPUUtilizationPercentage: 70` → scale up when CPU > 70%
- [ ] **VPA** (Vertical Pod Autoscaler) — recommend/set CPU+memory requests/limits automatically
- [ ] **PDB** (Pod Disruption Budget) — guarantee N pods are always available during node drain
  - `minAvailable: 1` — at least 1 acquiring-service pod always up during updates
- [ ] **NetworkPolicy** — firewall rules at pod level (only allow payment-switch → Postgres)

### Key Commands
- [ ] `kubectl apply -f deployment.yaml` — deploy/update resources from file
- [ ] `kubectl get pods -n payments-dev` — list pods in namespace
- [ ] `kubectl logs -f pod-name` — stream pod logs
- [ ] `kubectl exec -it pod-name -- bash` — shell into a running pod
- [ ] `kubectl describe pod pod-name` — full event history, probe failures, scheduling issues
- [ ] `kubectl rollout undo deployment/acquiring-service` — rollback to previous version
- [ ] `kubectl port-forward svc/acquiring-service 8080:8080` — expose service locally

### Helm — Package Manager for Kubernetes 🔥
- [ ] Helm chart: directory of templates that generate Kubernetes YAML
- [ ] `values.yaml` — default configuration; override per environment
- [ ] `helm install payments-acquiring ./charts/acquiring-service -f values-prod.yaml`
- [ ] `helm upgrade --install` — idempotent deploy (install if not exists, upgrade if exists)
- [ ] `helm rollback payments-acquiring 2` — rollback to release revision 2
- [ ] Template syntax: `{{ .Values.image.tag }}` — inject from values.yaml
- [ ] Subcharts: one parent chart with acquiring, switch, webhook-dispatcher as subchart deps
- [ ] `_helpers.tpl` — shared template fragments (labels, selectors, annotations)

### Local K8s with kind
- [ ] `kind` (Kubernetes IN Docker) — run full K8s cluster locally using Docker containers as nodes
- [ ] `kind create cluster --config kind-config.yaml` — 3 nodes (1 control-plane, 2 workers)
- [ ] Load local Docker image: `kind load docker-image acquiring-service:latest`
- [ ] MetalLB — local load balancer (kind doesn't have cloud LB)

### Probes
- [ ] **Liveness probe** — is the container healthy? Fail → kill + restart
  - `httpGet: /actuator/health/liveness` every 30s
- [ ] **Readiness probe** — can the container serve traffic? Fail → remove from Service endpoints
  - `httpGet: /actuator/health/readiness` every 10s
- [ ] **Startup probe** — is the container done starting? (prevents liveness killing slow starters)
  - `failureThreshold: 30` × `periodSeconds: 10` = 5 minutes to start before liveness kicks in

---

## 20. Infrastructure as Code — Terraform

### Why IaC 🔥
- [ ] Infrastructure defined in code — version controlled, reproducible, reviewable
- [ ] No manual console clicks → no "it works on my AWS account" problems
- [ ] Apply same infra to dev/qa/prod via workspaces with different `tfvars`

### Core Concepts
- [ ] **Provider** — plugin that talks to a cloud API (`hashicorp/aws`, `hashicorp/kubernetes`)
- [ ] **Resource** — an infrastructure object: `resource "aws_ecs_service" "acquiring" { ... }`
- [ ] **Data source** — read existing infrastructure: `data "aws_vpc" "main" { id = var.vpc_id }`
- [ ] **Variable** — input: `variable "environment" { type = string }` — overridden per workspace
- [ ] **Output** — export values: `output "rds_endpoint" { value = aws_db_instance.main.endpoint }`
- [ ] **Module** — reusable group of resources (e.g., `modules/ecs-service/`)

### State Management
- [ ] Terraform state: JSON file tracking real infrastructure vs code
- [ ] Remote state: S3 bucket + DynamoDB lock table (prevents concurrent applies)
  ```hcl
  backend "s3" {
    bucket         = "payments-platform-tfstate"
    key            = "prod/terraform.tfstate"
    region         = "ap-south-1"
    dynamodb_table = "terraform-state-lock"
  }
  ```
- [ ] `terraform plan` — show what will change (never apply without reviewing plan first)
- [ ] `terraform apply` — apply changes
- [ ] `terraform destroy` — tear down (always confirm before running)

### Modules Used
- [ ] `modules/ecs-service/` — reused for all 7 services (avoids repeating 100 lines per service)
- [ ] `modules/rds/` — Postgres with Multi-AZ, parameter group, subnet group
- [ ] `modules/redis/` — ElastiCache with Sentinel, subnet group, security group
- [ ] Workspaces: `terraform workspace select prod` — same code, different state + tfvars

---

## 21. GitOps — ArgoCD + Argo Rollouts

### GitOps Principles 🔥
- [ ] Git is the single source of truth for what should run in the cluster
- [ ] No manual `kubectl apply` — all changes go through Git (PR → merge → auto-apply)
- [ ] Drift detection: ArgoCD notices if someone manually changed K8s → auto-reverts
- [ ] Benefits: full audit trail (who changed what, when), easy rollback (revert Git commit)

### ArgoCD
- [ ] Watches a Git repo path; when changes merge → applies to cluster automatically
- [ ] **Application** — ArgoCD resource: "sync this Git path to this K8s namespace"
- [ ] **App of Apps** pattern: one root Application manages all service Applications
- [ ] **Sync waves** — ordering: `wave: 0` (namespace, config) before `wave: 1` (deployments) before `wave: 2` (ingress)
- [ ] `argocd app sync payments-acquiring` — manual sync trigger
- [ ] `argocd app rollback payments-acquiring 3` — rollback to a previous synced version
- [ ] Health checks: ArgoCD knows Deployment is healthy when `availableReplicas == desiredReplicas`

### Argo Rollouts — Canary + Blue-Green 🔥
- [ ] Extends K8s Deployment with advanced rollout strategies
- [ ] **Canary deployment**: route small % of traffic to new version, increase gradually
  ```yaml
  strategy:
    canary:
      steps:
        - setWeight: 5        # 5% → new version
        - pause: {}           # manual gate (or auto with AnalysisTemplate)
        - setWeight: 20
        - pause: {duration: 5m}
        - setWeight: 50
        - pause: {duration: 5m}
  ```
- [ ] **AnalysisTemplate** — define Prometheus query as rollout gate:
  - `success_rate > 0.99` AND `p95_latency < 500ms` → proceed
  - Query fails → automatic rollback to previous version
- [ ] **Blue-Green**: two full deployments (blue=current, green=new); switch traffic instantly
  - `argo rollouts promote acquiring-service` — switches 100% of traffic to green
  - Old blue kept running for instant rollback
- [ ] `kubectl argo rollouts get rollout acquiring-service --watch` — watch rollout progress
- [ ] Why not just K8s Deployment rolling update: K8s can't do %-based traffic splitting or auto-rollback on metrics

### KEDA — Kubernetes Event-Driven Autoscaling 🔥
- [ ] Autoscale based on external metrics, not just CPU (which K8s HPA does)
- [ ] **ScaledObject** — defines what triggers scaling
  ```yaml
  triggers:
    - type: kafka
      metadata:
        topic: transaction.events
        consumerGroup: webhook-dispatcher
        lagThreshold: "100"       # scale up when lag > 100 messages
  ```
- [ ] Scale-to-zero: KEDA can scale to 0 replicas when no work (settlement-service between runs)
  - K8s HPA minimum is 1; KEDA can go to 0 for batch jobs
- [ ] Other triggers: Redis queue length, Prometheus metric value, cron schedule
- [ ] Used for: webhook-dispatcher (Kafka lag), fraud-scoring-service (Redis queue), settlement (cron: scale up at 23:25, scale down at 00:00)

---

## 22. AI / LLM Engineering

### Large Language Models (LLMs) 🔥
- [ ] LLM: neural network trained on vast text → predicts next token given context
- [ ] **Context window**: maximum tokens model can process in one call (input + output)
- [ ] **Token**: roughly a word or word-piece; `"payments" = 1 token`, `"₹6000.00" ≈ 4 tokens`
- [ ] **Temperature**: controls randomness (0 = deterministic, 1 = creative); fraud scoring uses 0
- [ ] **System prompt**: fixed instructions prepended to every conversation
- [ ] Claude model IDs: Opus 4.7 (smartest), Sonnet 4.6 (balanced), Haiku 4.5 (fast/cheap)
- [ ] When to use each: Haiku for real-time fraud scoring (<50ms budget), Sonnet for batch analysis, Opus for ambiguous HIGH-risk decisions (extended thinking)

### Prompt Engineering 🔥
- [ ] System + user + assistant message structure
- [ ] Few-shot examples in prompt → model mimics pattern
- [ ] Chain-of-thought: "Think step by step" → better reasoning at cost of tokens
- [ ] Structured output: "Respond in JSON with keys: risk_level, confidence, reasoning"
- [ ] Prompt injection risk: never concatenate user input into prompt without sanitization

### Anthropic Prompt Caching 🔥
- [ ] Cache expensive static prefix (fraud rules, transaction schema = ~2000 tokens) across calls
- [ ] First call: write to cache — normal cost; Subsequent calls: read from cache — ~10x cheaper
  ```python
  messages = [{
      "role": "user",
      "content": [
          {"type": "text", "text": FRAUD_RULES_SCHEMA,
           "cache_control": {"type": "ephemeral"}},  # ← cache this
          {"type": "text", "text": f"Score this transaction: {txn_json}"}
      ]
  }]
  ```
- [ ] Cache TTL: 5 minutes; refresh by re-sending cached content before TTL expires
- [ ] Result: 90% cost reduction on fraud scoring calls (rules prefix dominates token count)

### Tool Calling / Function Calling 🔥
- [ ] LLM can call domain functions instead of inventing data
  ```python
  tools = [{
      "name": "check_velocity",
      "description": "Get PAN transaction count in last N minutes",
      "input_schema": {"type": "object", "properties": {
          "pan_hash": {"type": "string"},
          "window_minutes": {"type": "integer"}
      }}
  }]
  ```
- [ ] LLM responds with `tool_use` block → your code executes the function → pass result back → LLM continues
- [ ] Why: LLM can't access Redis/DB on its own; tool calling bridges domain logic
- [ ] Fraud agent tools: `check_velocity`, `get_geo_distance`, `check_mcc_risk`, `get_merchant_profile`

### Structured Outputs
- [ ] Force LLM to return valid JSON matching a schema
- [ ] Use `tool_choice: "any"` — model must call at least one tool → guaranteed JSON output
  ```python
  response = client.messages.create(
      tools=[FRAUD_SCORE_TOOL],
      tool_choice={"type": "any"},  # must call a tool
      messages=[...]
  )
  ```
- [ ] Why: JSON parsing of free-text LLM output is fragile; `tool_choice: "any"` is guaranteed

### Extended Thinking (Claude)
- [ ] Model reasons internally before responding — like chain-of-thought but internal
- [ ] `thinking: { type: "enabled", budget_tokens: 1024 }` in API call
- [ ] Used for: ambiguous HIGH-risk transactions where simple rules conflict
- [ ] Cost: thinking tokens counted; use sparingly (only for ~5% of edge cases)
- [ ] Why NOT for all fraud: adds 1-2 seconds to response — exceeds 50ms real-time budget

### Streaming LLM to Frontend 🔥
- [ ] LLM generates tokens one at a time — stream them to frontend as they arrive
- [ ] Backend: `stream=True` → `ReadableStream` (Node.js) or `StreamingResponse` (FastAPI)
- [ ] Frontend: consume via `ReadableStream` API, update UI on each chunk
  ```typescript
  const reader = response.body!.getReader();
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    setReasoningText(prev => prev + new TextDecoder().decode(value));
  }
  ```
- [ ] Used for: fraud reasoning tokens appearing live in Payment Simulator event log
- [ ] User experience: feels instant rather than waiting for full response

### LangGraph — Stateful Multi-Agent Graph 🔥
- [ ] LangGraph: framework for building stateful, graph-based LLM agents
- [ ] **Node** — a function that processes state and returns updated state
- [ ] **Edge** — conditional routing between nodes (if score > 0.8 → LLM node, else → return)
- [ ] **State** — typed dict shared across all nodes (`FraudState` with transaction, scores, reasoning)
- [ ] Fraud agent graph:
  ```
  RuleBasedNode → VectorSearchNode → [conditional] → LLMReasonNode → AggregatorNode
                                                  ↘ (BLOCK already) → AggregatorNode
  ```
- [ ] `StateGraph(FraudState)` → add nodes → add edges → `.compile()` → `.invoke(state)`
- [ ] Why vs simple chain: nodes can loop (retry), branch on intermediate results, share state naturally

### pgvector + Embeddings
- [ ] Embedding: convert text/transaction to a float vector (captures semantic meaning)
- [ ] Similar transactions → similar embeddings → close in vector space
- [ ] Fraud use case: find past transactions similar to current one → check if they were fraudulent
- [ ] Embedding pipeline: Debezium captures `transaction_events` → Kafka → Python consumer → Cohere embed API → INSERT INTO `transaction_embeddings` (pgvector)
- [ ] Query: `SELECT * FROM transaction_embeddings ORDER BY embedding <=> $1 LIMIT 5`
- [ ] HNSW index: approximate nearest neighbour, fast (milliseconds at 1M vectors)

### Langfuse — AI Observability 🔥
- [ ] Track every LLM call: prompt, response, latency, token count, cost, model version
- [ ] Traces: one fraud evaluation = one Langfuse trace with spans per LLM call + tool call
- [ ] Scores: human feedback or automated eval scores attached to traces
- [ ] Why: without observability, you can't improve prompts, debug failures, or track cost
- [ ] `langfuse.trace(name="fraud-evaluation", input=txn_json)` in Python fraud agent
- [ ] Self-hosted (Docker Compose) — no data leaves your infrastructure

### Evals — LLM Quality Measurement 🔥
- [ ] Eval: run your LLM pipeline against a labeled test set, measure quality metrics
- [ ] **ragas** — evaluate RAG pipeline quality (retrieval + generation)
  - Metrics: `context_recall`, `context_precision`, `answer_relevancy`, `faithfulness`
- [ ] **promptfoo** — compare prompt versions, model versions, run regression tests
  - `promptfoo eval --config evals/fraud-scoring.yaml` → produces comparison report
- [ ] Eval CI gate: `promptfoo` runs in CI → fail if `context_recall < 0.85`
- [ ] Baseline: run evals before any prompt change; require improvement or no regression

### Guardrails AI
- [ ] Validate LLM outputs before using them (prevent hallucination from reaching business logic)
- [ ] Validators: `ValidRange`, `ValidChoices`, `NoRefusal`, custom Python validators
- [ ] `Guard.use(ValidChoices(["LOW", "MEDIUM", "HIGH", "BLOCK"]))` on `risk_level` field
- [ ] On validation failure: re-ask model with correction instruction (up to N retries)
- [ ] Used for: ensure fraud score is always a valid `RiskLevel` enum value

### LiteLLM Gateway 🔥
- [ ] Unified API for calling multiple LLM providers with fallback
- [ ] Primary: Claude Haiku (fast, cheap) → Fallback: GPT-4o-mini → Fallback: Mistral
- [ ] Same `openai`-compatible API regardless of provider — no provider lock-in
  ```python
  from litellm import completion
  response = completion(model="claude-haiku-4-5", messages=[...])
  ```
- [ ] Automatic fallback: if Claude is down, LiteLLM transparently retries on GPT-4o-mini
- [ ] Budget limits: `max_budget=50` USD/month per project — prevents runaway LLM costs

### Context Window Management 🔥
- [ ] LLMs have finite context: 200K tokens (Claude) but each call costs money
- [ ] Sliding window: keep last N messages + system prompt; drop oldest when window full
- [ ] Summarization: when window approaches limit, summarize old messages into one paragraph
  ```python
  if count_tokens(messages) > MAX_TOKENS * 0.8:
      summary = llm.invoke("Summarize these fraud findings: " + str(old_messages))
      messages = [system_prompt, summary_message] + recent_messages[-5:]
  ```
- [ ] Why it matters: long fraud investigation chats accumulate history; without management → truncation → lost context

### MCP Servers (Model Context Protocol) 🔥
- [ ] Protocol that exposes domain tools to LLM-powered apps (like Claude Desktop)
- [ ] `payments-mcp-server`: expose payment domain tools over MCP
  - `get_transaction(id)` → returns Transaction details
  - `search_transactions(filter)` → merchant analytics
  - `trigger_reversal(txn_id)` → initiate reversal workflow
- [ ] Claude Desktop connects to your MCP server → ask questions → Claude calls your domain
- [ ] Implementation: FastAPI with MCP endpoints + JSON-RPC protocol

### MLflow + DVC — ML Lifecycle
- [ ] **MLflow**: track experiments (hyperparameters, metrics, artifacts), register models
  - `mlflow.log_metric("f1_score", 0.94)`, `mlflow.log_param("n_estimators", 100)`
  - Model registry: promote model versions (Staging → Production)
- [ ] **DVC** (Data Version Control): version large ML datasets like Git versions code
  - `dvc add data/fraud_transactions.parquet` → stores file in S3, tracks in Git
  - `dvc pull` — download the exact dataset version a collaborator used
- [ ] Together: reproducible ML experiments (same data + same code = same model)

### LoRA Fine-Tuning + vLLM
- [ ] **LoRA** (Low-Rank Adaptation): fine-tune LLM by training small adapter matrices (not full model)
  - 1% of full fine-tuning compute cost; works on consumer GPUs
  - Used for: adapt Mistral 7B to speak payments domain language fluently
- [ ] **QLoRA**: quantize base model to 4-bit → reduce VRAM requirement → fine-tune on cheaper GPU
- [ ] **PEFT + trl**: Hugging Face libraries for LoRA training
  ```python
  from peft import get_peft_model, LoraConfig
  lora_config = LoraConfig(r=16, target_modules=["q_proj", "v_proj"])
  model = get_peft_model(base_model, lora_config)
  ```
- [ ] **vLLM**: production LLM inference server — PagedAttention for efficient KV cache, 2-4x faster than Hugging Face
  - `vllm serve mistral-7b-fraud-v1 --tensor-parallel-size 2` — serve fine-tuned model

---

## 23. High Availability & Five 9s

### SLO / SLA Fundamentals 🔥
- [ ] 99.9% availability = 8.76 hours/year downtime
- [ ] 99.99% availability = 52.56 minutes/year downtime
- [ ] 99.999% (five 9s) = 5.26 minutes/year downtime
- [ ] This project targets: authorization ≥ 99.9%, webhook delivery ≥ 99.5%
- [ ] Error budget = 1 - SLO. At 99.9%: you have 43.8 min/month to spend on downtime
- [ ] Error budget burn rate: consuming budget 14x faster than normal → wake-up page
- [ ] Why SLOs matter: without a target, every outage feels equally bad; SLO gives context

### Infrastructure High Availability
- [ ] **Multi-AZ RDS**: standby replica in different AZ; failover in ~60s (DNS switch)
  - Write to primary; standby is not a read replica (no query offloading)
  - `multi_az = true` in Terraform RDS resource
- [ ] **Redis Sentinel (3-node)**: Sentinel quorum promotes replica if master down
  - Spring config: `spring.data.redis.sentinel.nodes: redis1:26379,redis2:26379,redis3:26379`
- [ ] **Kafka RF=3**: each partition has 3 replicas; survive 2 broker failures
  - `min.insync.replicas=2` — producer acks=all requires 2 replicas to confirm write
  - Guarantees: no message loss unless all 3 brokers fail simultaneously
- [ ] **Graceful shutdown**: drain in-flight requests before terminating
  - `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 30s`
  - Kubernetes sends SIGTERM → Spring drains → SIGKILL after 35s

### RTO and RPO
- [ ] **RTO** (Recovery Time Objective): maximum acceptable downtime after failure
  - acquiring-service: RTO 5 minutes (K8s restarts pod in < 1 min)
  - Postgres: RTO 2 minutes (Multi-AZ failover)
  - Redis: RTO 30 seconds (Sentinel promotion)
- [ ] **RPO** (Recovery Point Objective): maximum acceptable data loss after failure
  - Postgres: RPO 0 (synchronous Multi-AZ replication)
  - Kafka: RPO 0 (RF=3, acks=all)
  - Redis: RPO seconds (async replication to replica)

### Synthetic Monitoring
- [ ] Proactively test your system from outside (like a real user)
- [ ] Blackbox Exporter: probes HTTP endpoints from Prometheus, records latency/status
  - Probe `GET /actuator/health` every 30s from outside the cluster
  - Alert if `probe_success == 0` for > 2 minutes
- [ ] Why: health checks pass but real user-facing endpoint is broken → synthetic catches it
- [ ] Distinguish from: whitebox monitoring (metrics from inside the app)

---

## 24. Low Latency Engineering

### ZGC — Z Garbage Collector 🔥
- [ ] GC pauses < 1ms (vs G1GC: 50-200ms pauses) — critical for p99 latency
- [ ] Enable: `-XX:+UseZGC` in JVM args (also: `-XX:+ZGenerational` for throughput)
- [ ] Used for: acquiring-service + payment-switch (real-time path, p99 targets)
- [ ] Tradeoff: slightly higher CPU/memory overhead — acceptable for payment latency requirements
- [ ] Other GC options: G1GC (balanced, default), ParallelGC (throughput batch jobs), Shenandoah (similar to ZGC)

### Async Logging
- [ ] Problem: synchronous Logback writes to disk on every log line — adds latency to request thread
- [ ] Solution: `AsyncAppender` wraps existing appender — log writes go to in-memory queue, background thread flushes
  ```xml
  <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
      <appender-ref ref="CONSOLE"/>
      <queueSize>512</queueSize>
      <discardingThreshold>0</discardingThreshold>
  </appender>
  ```
- [ ] Tradeoff: small risk of losing last N log lines on crash — acceptable for latency improvement

### Rate Limiting Algorithms 🔥
- [ ] **Token bucket**: bucket holds N tokens; each request consumes 1; refilled at rate R/sec
  - Allows bursts up to bucket size; smooth steady-state
  - Used for: per-merchant authorization rate limit
- [ ] **Sliding window log**: store timestamp of each request; count in last N seconds
  - Accurate but memory-intensive (stores every request timestamp)
  - Used for: fraud velocity (PAN transactions in last 5 min)
- [ ] **Fixed window counter**: count requests in current time window; reset at window end
  - Allows 2x limit at window boundary (window transition burst)
  - Used for: daily merchant volume counter (acceptable boundary behavior)
- [ ] **Leaky bucket**: requests drip out at fixed rate regardless of burst; queue absorbs burst
  - Smooth output rate; adds latency for bursty traffic

### Connection Reuse
- [ ] HTTP connection pooling (`HttpClientConnectionManager`) — reuse TCP connections to HSM REST API
- [ ] gRPC `ManagedChannel` — single channel per service, HTTP/2 multiplexes many requests
- [ ] jPOS persistent TCP — one connection to each network for all transactions (never per-request)
- [ ] HikariCP — DB connection pool; creating new connection costs ~5-10ms
- [ ] Caffeine L1 cache — most BINs hot in JVM; saves Redis round-trip (~1ms) per transaction

### Protobuf over JSON
- [ ] Protobuf binary = 5-10x smaller than JSON equivalent
- [ ] Parsing: 3-5x faster than JSON (no string scanning, fixed field offsets)
- [ ] Used for: gRPC messages between acquiring-service and fraud-scoring-service
- [ ] Trade-off: not human-readable (need Protobuf tools to inspect) — acceptable for internal traffic

---

## 25. Modern System Design

### Event Sourcing 🔥
- [ ] Store sequence of events instead of current state snapshot
- [ ] `transaction_events`: every status change is a row — never UPDATE the transaction table
- [ ] Rebuild state: replay all events for a transaction → get current state
- [ ] Benefits: full audit trail, time-travel debugging, multiple read models from same events
- [ ] Snapshot optimization: at event N, save snapshot; new reads replay from snapshot + events after it
- [ ] Challenges: schema evolution (old events must still be replay-able after schema change)

### Full CQRS 🔥
- [ ] Write path: Command → Domain Service → event stored in `transaction_events` → Outbox
- [ ] Read path: Debezium reads WAL → Kafka → specialized read model consumers
  - `dashboard-consumer`: materializes `transactions_dashboard_view` (denormalized, fast reads)
  - `fraud-analytics-consumer`: materializes `fraud_velocity_view` (aggregations pre-computed)
- [ ] Read models are eventually consistent (milliseconds lag from write side)
- [ ] No JOINs on read side — data pre-joined in materialized views
- [ ] Interview framing: "We write events; read models are projections of those events"

### Layered Idempotency 🔥
- [ ] Layer 1: **Bloom filter** in Redis — `BF.EXISTS` is O(1) in-memory, no network hop
  - False positive rate 0.001% → 0.001% of requests hit layer 2 unnecessarily
- [ ] Layer 2: **Redis key** — `GET idempotency:{key}` — fast, shared across instances
- [ ] Layer 3: **Postgres UNIQUE constraint** — `INSERT … ON CONFLICT DO NOTHING` — guaranteed
- [ ] Layer 4: **Optimistic locking** (`@Version`) — database-level serialization
- [ ] First layer to catch duplicate = done; subsequent layers are safety nets
- [ ] Why layers: each layer is faster than the next; handle 99.9% at layer 1 for minimum latency

### Database Sharding vs Partitioning 🔥
- [ ] **Vertical partitioning**: split columns into separate tables (normalize)
- [ ] **Horizontal partitioning (sharding)**: split rows across multiple DB instances
  - Shard key: `merchant_id` → all transactions for a merchant on same shard
  - Challenges: cross-shard queries, rebalancing when adding shards
- [ ] **PostgreSQL table partitioning**: partition by range (date), list (network), or hash
  - `PARTITION BY RANGE (created_at)` → monthly partitions; old partitions archived
  - Still one DB; faster queries because partition pruning skips irrelevant data
- [ ] This project: no sharding yet; Postgres table partitioning by date for `transactions`

---

## 26. Security CI/CD Pipeline

### Why Shift-Left Security 🔥
- [ ] Find security issues during development, not in production
- [ ] Cost: fixing in dev is 10x cheaper than fixing post-prod deployment
- [ ] "Shift left" = move security checks earlier in the SDLC

### Tools in the Pipeline
- [ ] **Trivy** — container image vulnerability scanner
  - Scans Docker images for CVEs; fails build on CRITICAL vulnerabilities
  - `trivy image acquiring-service:latest --exit-code 1 --severity CRITICAL`
- [ ] **SonarQube** — static code analysis
  - Code smells, bugs, security hotspots, coverage gate
  - Quality gate: coverage ≥ 80%, no new BLOCKER issues
- [ ] **OWASP Dependency-Check** — scan dependencies for known CVEs
  - Fails build if any dependency has CVSS score ≥ 8.0
- [ ] **OWASP ZAP** — dynamic API security testing
  - Spins up your service, attacks it with known exploit patterns
  - Run against staging environment on every deploy
- [ ] **Semgrep** — semantic grep for code patterns (finds SQL injection, secret leaks)
- [ ] **detect-secrets** — scan codebase for accidentally committed secrets
  - `detect-secrets scan` → `.secrets.baseline` file → fail if new secrets found
- [ ] **Dependabot** — weekly PRs to update vulnerable dependencies automatically

### Security in Kubernetes
- [ ] NetworkPolicy: deny all by default, explicitly allow needed connections
- [ ] Pod Security Standards: `runAsNonRoot: true`, read-only root filesystem
- [ ] Secret management: K8s Secrets + External Secrets Operator syncing from AWS Secrets Manager
- [ ] RBAC: ServiceAccount per deployment, minimal permissions

---

## 27. k6 Load Testing + Chaos Engineering

### k6 Load Testing 🔥
- [ ] Write load tests in JavaScript; k6 runs them with thousands of virtual users
  ```javascript
  export default function() {
    http.post('http://acquiring-service/api/authorize', payload);
    check(res, { 'status is 200': (r) => r.status === 200 });
    sleep(0.1);
  }
  export const options = { vus: 500, duration: '60s' };
  ```
- [ ] Metrics: `http_req_duration` (p95, p99), `http_req_failed`, `iterations/s` (TPS)
- [ ] Performance gate: fail CI if `p95 > 500ms` or `error_rate > 0.1%`
- [ ] Target: 500 TPS with p95 < 500ms — verified with k6 load test
- [ ] k6 Cloud for distributed load from multiple regions

### LitmusChaos — Chaos Engineering 🔥
- [ ] Chaos engineering: intentionally inject failures to verify system resilience
- [ ] **Why**: "hope it doesn't break" is not a reliability strategy; prove it doesn't break
- [ ] Chaos experiments via ChaosEngine CRD in Kubernetes
- [ ] Experiments used:
  - `pod-delete`: randomly kill pods — verify K8s restarts and traffic recovers
  - `network-latency`: add 200ms latency to Kafka traffic — verify timeout handling
  - `pod-cpu-hog`: spike CPU on acquiring-service — verify load shedding kicks in
  - `node-drain`: drain a K8s node — verify PDB keeps 1 pod available
- [ ] **Steady-state hypothesis**: define what "healthy" means → run chaos → verify still healthy
- [ ] `litmus run --experiment pod-delete --namespace payments-prod` (never in prod without approval)
- [ ] Schedule: weekly in staging; quarterly in prod (GameDay)

---

## 28. Kong API Gateway

### What an API Gateway Does 🔥
- [ ] Single entry point for all external API traffic
- [ ] Handles cross-cutting concerns: rate limiting, auth, logging, routing — so services don't have to
- [ ] Used for: all traffic from Payment Simulator UI + external merchants to acquiring-service

### Kong Plugins Used
- [ ] **Rate limiting**: `rate-limiting` plugin — 100 requests/minute per API key
- [ ] **API key auth**: `key-auth` plugin — validates `X-Api-Key`, injects consumer identity
- [ ] **Request logging**: `file-log` / OTel plugin — log all requests to Loki
- [ ] **Prometheus**: expose Kong metrics (requests, latency, errors per route)
- [ ] **Correlation ID**: inject `X-Correlation-ID` into every request for tracing
- [ ] **CORS**: configure allowed origins for browser requests

### Kong vs ALB
- [ ] ALB: L7 routing, SSL termination, health checks — AWS-managed
- [ ] Kong: application-layer gateway, plugin ecosystem, custom logic
- [ ] Used together: ALB terminates SSL, routes to Kong, Kong handles business logic

---

## 29. Learning Path — Comprehensive

### Phase 1 — Java + Architecture Foundation (2 weeks)
- [ ] Java records, sealed interfaces, pattern matching switch (CLAUDE.md §2.6, §2.7)
- [ ] BigDecimal arithmetic — build a `Money` record, write 10 tests for edge cases
- [ ] Hexagonal architecture — draw the three layers; trace `processPayment` from HTTP to DB
- [ ] SOLID — identify one violation of each principle, explain why it's a violation
- [ ] TDD red-green-refactor — implement `TransactionStateMachine` test-first
- [ ] ArchUnit — write a rule that fails if any domain class imports Spring
- [ ] JaCoCo — add coverage gate; deliberately drop coverage below 90% and see build fail

### Phase 2 — Spring Boot + Databases (2 weeks)
- [ ] Spring Data JPA — build a repository with `@Query`, `@Transactional`, `@BatchSize`
- [ ] jOOQ — write a settlement aggregation query with GROUP BY, SUM using DSLContext
- [ ] Flyway — write V1-V3 migration files; break one and understand what happens
- [ ] Testcontainers — write an integration test that hits a real Postgres container
- [ ] Optimistic locking — deliberately trigger `OptimisticLockException` in a test
- [ ] N+1 problem — write a query that N+1s, observe in `show-sql: true`, fix with JOIN FETCH

### Phase 3 — Redis + Kafka (1 week)
- [ ] Redis — set a key with TTL, watch it expire; implement distributed lock with SETNX
- [ ] Bloom filter — understand false positive rate; add `BF.ADD` / `BF.EXISTS` to idempotency flow
- [ ] Kafka — produce and consume a message; deliberately don't commit offset, crash, observe replay
- [ ] Schema Registry + Avro — define a `.avsc` schema, evolve it, verify backward compatibility
- [ ] Debezium — set up local Debezium connector against Postgres; observe WAL changes in Kafka

### Phase 4 — Resilience + Fault Tolerance (1 week)
- [ ] Circuit breaker — use Resilience4j `@CircuitBreaker`; trigger OPEN state by failing 5/10 calls
- [ ] Bulkhead — configure semaphore bulkhead; observe `BulkheadFullException` under load
- [ ] Rate limiter — configure `@RateLimiter`; write a test that triggers `RequestNotPermitted`
- [ ] Time limiter — wrap a `CompletableFuture` with 500ms budget; test with a slow mock
- [ ] Load shedding — implement reject-at-80% logic; verify with a load test

### Phase 5 — Observability (1 week)
- [ ] MDC — add `transactionId` to MDC in a filter; verify it appears in all log lines
- [ ] OpenTelemetry — add auto-instrumentation; view a trace in Grafana Tempo
- [ ] Prometheus — add a `Counter` and a `Histogram`; scrape in Prometheus; graph in Grafana
- [ ] SLO dashboard — create a Grafana dashboard with success rate and p95 latency
- [ ] Error budget — calculate current burn rate from Prometheus data
- [ ] Sentry — set up Sentry; throw a test exception; view in Sentry with full context

### Phase 6 — APIs: gRPC + GraphQL (1 week)
- [ ] Protobuf — write a `.proto`, run `protoc`, use generated Java classes
- [ ] gRPC — build a simple client/server; add an interceptor for logging
- [ ] gRPC streaming — implement server-streaming RPC; consume stream in client
- [ ] GraphQL — define a schema; implement `@QueryMapping` and `@MutationMapping`
- [ ] GraphQL subscription — stream events via `@SubscriptionMapping` (Reactor Publisher)
- [ ] DataLoader — implement `@BatchMapping` to solve N+1 in GraphQL

### Phase 7 — DevOps: Containers + K8s (2 weeks)
- [ ] Docker multi-stage build — build, run locally; inspect image layers
- [ ] kind — create a 3-node local cluster; deploy acquiring-service with Helm
- [ ] Helm — write a chart with values.yaml; override values for dev vs prod
- [ ] K8s probes — configure liveness + readiness + startup; trigger a readiness failure
- [ ] HPA — configure CPU-based autoscaling; trigger scale-up with a load test
- [ ] PDB — configure `minAvailable: 1`; drain a node, verify 1 pod stays up
- [ ] NetworkPolicy — write deny-all + allow-specific rules; test with curl from wrong pod

### Phase 8 — GitOps + Canary (1 week)
- [ ] ArgoCD — install locally; create an Application pointing to a Git path; make a change and watch sync
- [ ] Argo Rollouts — deploy with canary strategy; observe 5% → 20% → 50% traffic split
- [ ] AnalysisTemplate — define Prometheus gate; deploy bad version; watch auto-rollback
- [ ] KEDA — configure Kafka-lag-based scaling; produce messages; watch pods scale up

### Phase 9 — Infrastructure as Code (1 week)
- [ ] Terraform — write a module for an S3 bucket; `plan`, `apply`, verify in AWS console
- [ ] Remote state — configure S3 backend + DynamoDB lock; simulate concurrent apply
- [ ] Modules — refactor from flat resources to a `modules/ecs-service/` module
- [ ] Workspaces — create `dev` and `prod` workspaces; apply same code to both

### Phase 10 — TypeScript + Next.js (1 week)
- [ ] TypeScript basics — strict mode; generics; discriminated unions; `z.infer<typeof schema>`
- [ ] Next.js App Router — create a Server Component that fetches data; add a Client Component with state
- [ ] SSR vs SSG vs ISR — understand `cache: 'no-store'` vs `revalidate: 3600`
- [ ] Streaming SSR — add `loading.tsx`; observe how partial HTML arrives in browser
- [ ] shadcn/ui — `npx shadcn add table`; use the component in a page
- [ ] React Query (TypeScript) — typed `useQuery<TransactionSummary[]>`; observe cache behavior
- [ ] Playwright — write E2E test: navigate to simulator, fill form, assert authorized

### Phase 11 — AI/LLM Engineering (2 weeks)
- [ ] Claude API basics — make a simple API call; understand system + user + assistant messages
- [ ] Prompt engineering — write few-shot prompt for fraud scoring; test with edge cases
- [ ] Tool calling — define a tool, observe model call it, pass result back, observe reasoning
- [ ] Structured outputs — use `tool_choice: "any"` to force JSON; validate with Guardrails AI
- [ ] Streaming — stream a response to terminal; observe tokens arrive in real time
- [ ] Prompt caching — add `cache_control` to large static prefix; measure cost reduction
- [ ] Extended thinking — enable thinking tokens; compare reasoning vs non-thinking response
- [ ] LangGraph — build a simple 3-node graph (rule → vector-search → llm); add conditional edge
- [ ] pgvector — insert an embedding; query with cosine similarity; add HNSW index
- [ ] Langfuse — instrument an LLM call; view trace in Langfuse UI
- [ ] ragas eval — write 10 labeled Q&A pairs; run `ragas evaluate`; interpret metrics
- [ ] LiteLLM — configure primary + fallback; test fallback by pointing primary at wrong endpoint
- [ ] MCP server — expose a `get_transaction` tool; connect Claude Desktop; ask it a question

### Phase 12 — High Availability + Load Testing (1 week)
- [ ] k6 — write a load test script; run at 500 VUs; analyze p95/p99 in results
- [ ] ZGC — switch from G1GC to ZGC; run load test; compare GC pause metrics
- [ ] Chaos with LitmusChaos — run `pod-delete` experiment; verify steady-state hypothesis
- [ ] Pact contract tests — write a consumer Pact; publish to broker; verify on provider
- [ ] PIT mutation testing — run `mvn pitest:mutationCoverage`; kill surviving mutants

---

## 30. Resources Reference

Curated public resources for every pillar of this project.
Legend: 📖 Book · 🆓 Free · 💰 Paid · ⭐ Must-read/watch first

---

### Java + Spring Boot

| Resource | Type | Notes |
|---|---|---|
| [Baeldung](https://www.baeldung.com) | 🆓 | Best free Spring/Java articles; search any topic + "baeldung" |
| [Spring Official Guides](https://spring.io/guides) | 🆓 | Short hands-on tutorials for every Spring module |
| [Spring Boot Reference Docs](https://docs.spring.io/spring-boot/docs/current/reference/html/) | 🆓 | The authoritative reference — search when stuck |
| *Effective Java* — Joshua Bloch | 📖💰 ⭐ | Best Java book ever written; read items 1–50 |
| [Java 21 What's New](https://openjdk.org/projects/jdk/21/) | 🆓 | Official JEPs for records, sealed interfaces, virtual threads, pattern matching |
| [Baeldung — Java 21 Features](https://www.baeldung.com/java-21-new-features) | 🆓 | Practical examples of every Java 21 feature used in this project |
| [jOOQ Getting Started](https://www.jooq.org/doc/latest/manual/getting-started/) | 🆓 | Official jOOQ docs — start with "Tutorial" section |

---

### Architecture + System Design

| Resource | Type | Notes |
|---|---|---|
| *Designing Data-Intensive Applications* — Martin Kleppmann | 📖💰 ⭐ | **The** distributed systems book; covers Kafka, DBs, replication, CAP, CQRS |
| *Implementing Domain-Driven Design* — Vaughn Vernon | 📖💰 ⭐ | Deep dive on hexagonal arch, aggregates, domain events, bounded contexts |
| *Clean Architecture* — Robert C. Martin | 📖💰 | Ports & adapters explained clearly; read after Vernon |
| [Martin Fowler's Blog](https://martinfowler.com) | 🆓 | Canonical articles on CQRS, Event Sourcing, Saga, Outbox, Strangler Fig |
| [System Design Primer](https://github.com/donnemartin/system-design-primer) | 🆓 ⭐ | GitHub repo; 200k+ stars; covers every interview topic with diagrams |
| [ByteByteGo Newsletter + YouTube](https://bytebytego.com) | 🆓/💰 | Alex Xu's visual system design explanations; free YouTube is excellent |

---

### Testing

| Resource | Type | Notes |
|---|---|---|
| [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/) | 🆓 | Official docs; all annotations, parameterized tests, extensions |
| [Mockito Documentation](https://site.mockito.org) | 🆓 | Official; start with "How to write good tests" section |
| [Testcontainers Guides](https://testcontainers.com/guides/) | 🆓 ⭐ | Step-by-step guides for Postgres, Kafka, Redis with Spring Boot |
| [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html) | 🆓 | All rule types with examples; read the "Concepts" chapter first |
| [WireMock Docs](https://wiremock.org/docs/) | 🆓 | Stubs, scenarios, fixed delays — used for chaos tests |
| [Pact Docs](https://docs.pact.io) | 🆓 | Consumer-driven contract testing; start with "How Pact works" |
| [PIT Mutation Testing](https://pitest.org/quickstart/maven/) | 🆓 | Maven quickstart; understand mutation score vs line coverage |

---

### Databases + Caching

| Resource | Type | Notes |
|---|---|---|
| [PostgreSQL Documentation](https://www.postgresql.org/docs/current/) | 🆓 ⭐ | Best DB docs in existence; read chapters on indexes, EXPLAIN, transactions |
| [Use the Index, Luke](https://use-the-index-luke.com) | 🆓 ⭐ | Free book on SQL indexing — understand partial indexes, EXPLAIN ANALYZE |
| [Flyway Documentation](https://documentation.red-gate.com/flyway) | 🆓 | Migrations, versioning, repair; short read |
| [PgBouncer Docs](https://www.pgbouncer.org/config.html) | 🆓 | Config reference; understand transaction vs session mode |
| [Redis Documentation](https://redis.io/docs/) | 🆓 | Official; data structures, TTL, pub/sub, persistence |
| [Redis University](https://university.redis.com) | 🆓 ⭐ | Free courses: RU101 (Intro), RU330 (Security), RU203 (Streams) |
| [pgvector GitHub](https://github.com/pgvector/pgvector) | 🆓 | README has all you need: HNSW vs IVFFlat, cosine vs L2, indexing |
| [Elasticsearch Guide](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html) | 🆓 | Official docs; start with "Getting started" |

---

### Kafka + Messaging

| Resource | Type | Notes |
|---|---|---|
| [Kafka: The Definitive Guide](https://www.confluent.io/resources/kafka-the-definitive-guide-v2/) | 🆓 ⭐ | Free PDF from Confluent; chapters 1–6 are essential |
| [Confluent Developer Tutorials](https://developer.confluent.io/learn-kafka/) | 🆓 ⭐ | Hands-on Kafka courses; "Kafka for Spring" is directly relevant |
| [Debezium Documentation](https://debezium.io/documentation/reference/stable/) | 🆓 | CDC docs; start with "Tutorial" then Postgres connector config |
| [Confluent Schema Registry Docs](https://docs.confluent.io/platform/current/schema-registry/) | 🆓 | Schema evolution rules, Avro integration with Kafka |
| [Apache Avro Spec](https://avro.apache.org/docs/current/specification/) | 🆓 | Avro schema syntax; field types, default values, evolution rules |

---

### Resilience + Fault Tolerance

| Resource | Type | Notes |
|---|---|---|
| [Resilience4j Docs](https://resilience4j.readme.io/docs) | 🆓 ⭐ | Official; all patterns with Spring Boot config examples |
| [Temporal Documentation](https://docs.temporal.io) | 🆓 ⭐ | Official; start with "Core concepts" then Java SDK tutorial |
| [Release It!](https://pragprog.com/titles/mnee2/release-it-second-edition/) — Michael Nygard | 📖💰 | Original circuit breaker book; stability patterns in production |

---

### Observability

| Resource | Type | Notes |
|---|---|---|
| [Grafana Documentation](https://grafana.com/docs/) | 🆓 | Covers Grafana, Loki, Tempo, Mimir — unified docs site |
| [Grafana Free Tutorials](https://grafana.com/tutorials/) | 🆓 ⭐ | Hands-on: "Getting started with Grafana and Prometheus" is the right start |
| [OpenTelemetry Docs](https://opentelemetry.io/docs/) | 🆓 | Java auto-instrumentation, collector config, Prometheus exporter |
| [Prometheus Docs](https://prometheus.io/docs/introduction/overview/) | 🆓 | PromQL, metric types, alerting rules |
| [Sentry Docs](https://docs.sentry.io) | 🆓 | Spring Boot + Next.js SDKs; release tracking setup |
| [Google SRE Book](https://sre.google/sre-book/table-of-contents/) | 🆓 ⭐ | Free online; chapters on SLOs, error budgets, toil — read chapters 2–6 |

---

### DevOps: Docker + Kubernetes + Helm

| Resource | Type | Notes |
|---|---|---|
| [Docker Get Started](https://docs.docker.com/get-started/) | 🆓 | Official tutorial; multi-stage builds in Part 9 |
| [Play with Docker](https://labs.play-with-docker.com) | 🆓 | Free browser-based Docker playground |
| [Kubernetes Official Docs](https://kubernetes.io/docs/home/) | 🆓 ⭐ | Best K8s reference; "Concepts" section is required reading |
| *Kubernetes in Action* — Marko Luksa | 📖💰 ⭐ | Best K8s book; builds from pods up to full production patterns |
| [KodeKloud K8s Course](https://kodekloud.com/courses/kubernetes-for-the-absolute-beginner-hands-on/) | 💰 | Paid but cheap; best hands-on intro with browser labs |
| [Helm Docs](https://helm.sh/docs/) | 🆓 | Chart template reference; start with "Getting Started" |
| [ArgoCD Documentation](https://argo-cd.readthedocs.io/en/stable/) | 🆓 | App of apps, sync waves, RBAC — all in official docs |
| [Argo Rollouts Docs](https://argoproj.github.io/rollouts/) | 🆓 | Canary + blue-green strategies, AnalysisTemplate examples |
| [KEDA Documentation](https://keda.sh/docs/) | 🆓 | Scalers reference: Kafka, Redis, Prometheus, cron |

---

### Infrastructure as Code

| Resource | Type | Notes |
|---|---|---|
| [HashiCorp Terraform Tutorials](https://developer.hashicorp.com/terraform/tutorials) | 🆓 ⭐ | Official interactive tutorials; "AWS Get Started" track is the right entry point |
| [Terraform: Up & Running](https://www.terraformupandrunning.com) — Yevgeniy Brikman | 📖💰 | Best Terraform book; modules, state, workspaces all covered well |

---

### APIs: gRPC + GraphQL

| Resource | Type | Notes |
|---|---|---|
| [gRPC Official Docs](https://grpc.io/docs/) | 🆓 ⭐ | Start with "Introduction to gRPC" then Java quickstart |
| [Protocol Buffers Docs](https://protobuf.dev/getting-started/) | 🆓 | Protobuf syntax, field types, evolution rules |
| [Baeldung — gRPC with Spring](https://www.baeldung.com/grpc-introduction) | 🆓 | Practical Spring Boot gRPC setup |
| [How to GraphQL](https://www.howtographql.com) | 🆓 ⭐ | Free full-stack tutorial; covers schema, resolvers, subscriptions |
| [GraphQL Official Docs](https://graphql.org/learn/) | 🆓 | Spec + learn section; good for understanding the "why" |
| [Baeldung — Spring for GraphQL](https://www.baeldung.com/spring-graphql) | 🆓 | @QueryMapping, DataLoader, subscriptions with Spring |

---

### TypeScript + Next.js Frontend

| Resource | Type | Notes |
|---|---|---|
| [TypeScript Handbook](https://www.typescriptlang.org/docs/handbook/intro.html) | 🆓 ⭐ | Official; read cover to cover (it's short) — generics and utility types are key |
| [Total TypeScript](https://www.totaltypescript.com) — Matt Pocock | 🆓/💰 | Free articles + paid workshops; best advanced TypeScript resource |
| [Next.js Documentation](https://nextjs.org/docs) | 🆓 ⭐ | Official docs are exceptionally well written; App Router section is the focus |
| [Next.js Learn Course](https://nextjs.org/learn) | 🆓 | Official interactive course; builds a dashboard app (very similar to this project) |
| [TanStack Query Docs](https://tanstack.com/query/latest/docs/framework/react/overview) | 🆓 | React Query v5 official docs; guides + API reference |
| [Zustand Docs](https://zustand.docs.pmnd.rs) | 🆓 | Short docs; read in 30 minutes; TypeScript section is important |
| [shadcn/ui Docs](https://ui.shadcn.com/docs) | 🆓 | Component installation + usage; Theming section for Tailwind integration |
| [Playwright Docs](https://playwright.dev/docs/intro) | 🆓 | Installation, locators, assertions — "Getting Started" + "Best Practices" |
| [Tailwind CSS Docs](https://tailwindcss.com/docs) | 🆓 | Full utility reference; scan once, bookmark, search as needed |

---

### AI / LLM Engineering

| Resource | Type | Notes |
|---|---|---|
| [Anthropic Documentation](https://docs.anthropic.com) | 🆓 ⭐ | API reference, prompt engineering guide, tool use, streaming, prompt caching |
| [Anthropic Prompt Engineering Guide](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/overview) | 🆓 ⭐ | Official; covers few-shot, CoT, XML tags, system prompts |
| [DeepLearning.AI Short Courses](https://www.deeplearning.ai/short-courses/) | 🆓 ⭐ | Free; "LangChain for LLM App Dev", "Building Systems with ChatGPT API", "AI Agents" |
| [LangGraph Docs](https://langchain-ai.github.io/langgraph/) | 🆓 | Official; conceptual guide + Python tutorials; start with "Why LangGraph?" |
| [Langfuse Documentation](https://langfuse.com/docs) | 🆓 | LLM tracing, evals, prompt management; self-hosting guide |
| [ragas Documentation](https://docs.ragas.io) | 🆓 | RAG evaluation metrics; quickstart shows how to run your first eval |
| [promptfoo Documentation](https://www.promptfoo.dev/docs/intro) | 🆓 | Prompt regression testing; config format and CI integration |
| [vLLM Documentation](https://docs.vllm.ai/en/latest/) | 🆓 | Serving fine-tuned models; OpenAI-compatible API setup |
| [Hugging Face PEFT Docs](https://huggingface.co/docs/peft) | 🆓 | LoRA/QLoRA implementation; practical fine-tuning guide |
| [MLflow Documentation](https://mlflow.org/docs/latest/index.html) | 🆓 | Experiment tracking + model registry; Python quickstart |
| [DVC Documentation](https://dvc.org/doc/start) | 🆓 | Data versioning + pipeline stages; "Get Started" covers the core in 30 min |

---

### Payments Domain

| Resource | Type | Notes |
|---|---|---|
| [jPOS Programmer's Guide](http://jpos.org/doc/proguide.pdf) | 🆓 ⭐ | Free PDF; the definitive jPOS reference for ISO 8583 in Java |
| [EMVCo Specifications](https://www.emvco.com/emv-technologies/contact/) | 🆓 | Official EMV specs (free registration required); Book 2 covers ARQC/ARPC |
| [NPCI UPI Ecosystem](https://www.npci.org.in/what-we-do/upi/product-overview) | 🆓 | UPI product overview and ecosystem documentation |
| [ISO 8583 Wikipedia](https://en.wikipedia.org/wiki/ISO_8583) | 🆓 | Good overview of MTIs, bitmaps, field definitions — start here |
| *Payments Systems in the U.S.* — Carol Coye Benson | 📖💰 | US-centric but foundational; authorization, clearing, settlement explained clearly |

---

### Security

| Resource | Type | Notes |
|---|---|---|
| [OWASP Top 10](https://owasp.org/www-project-top-ten/) | 🆓 ⭐ | The 10 most critical web application security risks with mitigations |
| [OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org) | 🆓 | Quick reference on authentication, HMAC, SQL injection prevention, etc. |
| [Trivy Documentation](https://aquasecurity.github.io/trivy/) | 🆓 | Container + filesystem scanning; CI integration guide |
| [SonarQube Docs](https://docs.sonarsource.com/sonarqube/latest/) | 🆓 | Quality gates, security hotspots, Maven plugin config |
| [detect-secrets GitHub](https://github.com/Yelp/detect-secrets) | 🆓 | Usage + baseline setup; CI integration in README |

---

### Load Testing + Chaos

| Resource | Type | Notes |
|---|---|---|
| [k6 Documentation](https://grafana.com/docs/k6/latest/) | 🆓 ⭐ | Official; scripting, metrics, thresholds, CI integration |
| [LitmusChaos Documentation](https://docs.litmuschaos.io) | 🆓 | Experiments catalog; ChaosEngine CRD; steady-state hypothesis |
| *Chaos Engineering* — Casey Rosenthal | 📖💰 | Principles and practices; written by Netflix chaos engineers |

---

### YouTube Channels Worth Following

| Channel | What it covers |
|---|---|
| [ByteByteGo](https://www.youtube.com/@ByteByteGo) | System design visuals — Kafka, Redis, consistent hashing, rate limiting |
| [TechWorld with Nana](https://www.youtube.com/@TechWorldwithNana) | K8s, Docker, ArgoCD, Terraform hands-on tutorials |
| [Confluent](https://www.youtube.com/@Confluent) | Kafka deep dives, Debezium, Schema Registry talks |
| [Anton Putra](https://www.youtube.com/@AntonPutra) | Kubernetes, Prometheus, Grafana, ArgoCD |
| [Fireship](https://www.youtube.com/@Fireship) | TypeScript, Next.js, AI concepts — fast 100-second overviews |
| [Theo (t3.gg)](https://www.youtube.com/@t3dotgg) | TypeScript, Next.js, React ecosystem opinions |
| [Matt Pocock](https://www.youtube.com/@mattpocockuk) | Advanced TypeScript patterns |
| [Anthropic on YouTube](https://www.youtube.com/@anthropic-ai) | Claude capabilities, prompt engineering, AI safety |

---

### Interactive Platforms

| Platform | Best for |
|---|---|
| [Killercoda](https://killercoda.com) | Free browser-based K8s, Docker, Linux labs — no local setup |
| [Play with Kubernetes](https://labs.play-with-k8s.com) | Free 4-hour K8s playground in browser |
| [Grafana Play](https://play.grafana.org) | Live Grafana instance with demo dashboards — explore without installing |
| [Confluent Cloud Free Tier](https://www.confluent.io/confluent-cloud/tryfree/) | Managed Kafka free tier — good for learning Schema Registry |
| [HashiCorp Instruqt](https://developer.hashicorp.com/terraform/tutorials) | Interactive Terraform tutorials run in browser |

---

*This checklist covers every concept used across core (v1.0.0) and overengineered (v2.0.0) versions.*
*Check off items as you understand them with enough depth to explain in an interview.*
*Last updated: May 2026 — after CLAUDE.md v6.0 (full v2.0.0 overengineering roadmap)*
