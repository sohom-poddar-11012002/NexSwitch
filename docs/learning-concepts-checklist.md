# Learning Concepts Checklist — NexSwitch
### Complete Prep Plan · Concept Checklist · Resources · Timeline
*7–8h/day till June 30 (4h learning + 3–4h project) → 4–5h/day July–December (DSA/System Design practice)*

---

## YOUR SITUATION IN ONE PARAGRAPH

You are building a production-grade payments platform (Payswiff) as your primary vehicle of learning until June 30. Afternoons (4h) go to concept study — this document. Evenings (3–4h) go to actually building the project. From July, evenings shift to DSA + system design + mock interview practice, and the project continues only on weekends. This is close to the ideal prep structure: you study a concept in the afternoon and wire it into a real system the same evening. By September you'll have ~900h of learning+building and ~500h of interview-specific drill. By December, ~1,500h total. That puts you at 85–90% readiness for fintech SDE2 roles by September and 90–95% by December.

---

## MASTER TIMELINE

```
May 13 → June 30   (7 weeks)   FOUNDATION PHASE
  Afternoons: 4h concept learning (this checklist, in order)
  Evenings:   3–4h project building (Payswiff v1 → v2)
  Total:      ~900–1,000h by June 30

July 1 → Sep 30    (13 weeks)  DRILL PHASE
  Afternoons: 4h DSA + system design + mock interviews
  Evenings:   DSA/system design practice continues
  Weekends:   Project enhancements only
  Total:      +~500h by Sep 30 (cumulative ~1,400–1,500h)

Oct 1 → Dec 31     (13 weeks)  POLISH + APPLY PHASE
  Same structure; start real applications from October
  Target: 2 mock interviews/week minimum throughout
```

---

## PROBABILITY OF SUCCESS

| Milestone | Fintech / Payments SDE2 | Strong Product Co. | FAANG India |
|---|---|---|---|
| June 30 | Not applying yet — building | — | — |
| Sep 30 | **85–90%** | **75–80%** | **60–65%** |
| Dec 31 | **92–95%** | **85–90%** | **72–78%** |

**What could hurt you:** not doing mock interviews from July (knowledge ≠ performance under pressure), DSA neglect, passive reading without hands-on coding.

**What will help you:** the project IS your system design prep. When an interviewer asks you to design a rate limiter or an outbox system, you've built one.

---

## HOW TO USE THIS DOCUMENT

- Items marked 🔥 are **frequently asked in SDE2 interviews** — prioritize these
- **P0** = cannot walk into an interview without this; must be fluent by September
- **P1** = expected at SDE2 level; be able to discuss trade-offs
- **P2** = depth / differentiation; be familiar, can discuss if asked
- Check off items when you can **explain them out loud without notes**, not just when you've read them
- The Learning Path (Section 29) has been replaced with the phased plan at the top and bottom of this document

---

# PART ONE — CONCEPT CHECKLIST

---

## 1. Java Language Features

### Java Basics — P0
- [ ] Classes vs interfaces vs abstract classes — when to use each; interfaces can have `default` methods since Java 8
- [ ] Access modifiers: `public`, `protected`, `private`, package-private — strictest to most open
- [ ] `final` keyword — on variables (no reassign), methods (no override), classes (no extend)
- [ ] `static` keyword — shared across all instances; static methods cannot access instance state
- [ ] `null` handling — why NullPointerException exists; use `Optional<T>` and `Objects.requireNonNull()`

### Java Collections — P0
- [ ] `List`, `Set`, `Map` — interfaces vs implementations (ArrayList, HashSet, HashMap)
- [ ] `ArrayList` vs `LinkedList`, `HashMap` vs `LinkedHashMap` vs `TreeMap` — when to use each
- [ ] `Map.ofEntries()` — immutable maps; used in `TransactionStatus` valid-transitions map
- [ ] Iterating: `forEach`, streams, `entrySet()` — know all three styles

### Java 8+ Features — P0
- [ ] Lambda expressions — `(x) -> x.doSomething()`; anonymous functions; closure over effectively-final variables
- [ ] Stream API — `filter()`, `map()`, `collect()`, `reduce()`, `forEach()`, `findFirst()` — write 10 examples from scratch
- [ ] `Optional<T>` — `of()`, `ofNullable()`, `orElse()`, `orElseThrow()`, `map()`, `flatMap()` — avoids null checks
- [ ] Method references — `SomeClass::method`; know all 4 kinds (static, instance, constructor, arbitrary instance)
- [ ] `Instant` — timezone-safe timestamp; always use instead of `Date`
- [ ] `Duration` — time intervals: `Duration.ofSeconds(15)`, `Duration.ofMinutes(5)`
- [ ] `LocalDate`, `LocalDateTime` — for settlement dates and batch runs

### Java 17–21 Features — P0 (used heavily in this project)
- [ ] **Records** 🔥 — `public record Money(BigDecimal amount, Currency currency) {}`
  - Immutable; auto-generates constructor, `equals()`, `hashCode()`, `toString()`
  - Compact constructors — validation inside records without repeating field names
  - Used for: Money, MerchantId, Transaction, QRSession, all domain models
- [ ] **Sealed interfaces** 🔥 — `sealed interface AuthorizationResult permits Approved, Declined, Unknown`
  - Compiler forces exhaustive handling of all permitted subtypes
  - Used for: AuthorizationResult, all result types
- [ ] **Pattern matching switch** 🔥 — `switch (result) { case Approved a -> ...; case Declined d -> ...; }`
  - Replaces instanceof chains; exhaustive check at compile time
- [ ] **Text blocks** — multiline strings with `"""`; cleaner SQL and JSON in tests
- [ ] **Virtual threads** 🔥 — JVM-managed lightweight threads; 1M concurrent vs ~10k OS threads
  - Enabled in Spring Boot 3.2+: `spring.threads.virtual.enabled=true`
  - No code changes needed — Spring wraps each request in a virtual thread
- [ ] **`instanceof` pattern matching** — `if (obj instanceof String s) { s.length(); }` — no cast needed

### BigDecimal — Money Arithmetic — P0
- [ ] Why `double` is wrong for money — `0.1 + 0.2 = 0.30000000000000004` (IEEE 754 floating point)
- [ ] Always use String constructor: `new BigDecimal("6000.00")` — never `new BigDecimal(6000.00)`
- [ ] `setScale(2, RoundingMode.HALF_UP)` — always specify scale and rounding mode
- [ ] `compareTo()` vs `equals()` — `equals()` is scale-sensitive: `2.0 ≠ 2.00`; use `compareTo()` for math
- [ ] `divide(divisor, 2, RoundingMode.HALF_UP)` — always specify scale when dividing or get `ArithmeticException`
- [ ] `NUMERIC(15,2)` in Postgres — exact SQL equivalent of BigDecimal; never use `FLOAT` or `DOUBLE` for money

### Exception Handling — P1
- [ ] Checked vs unchecked: `Exception` (must be caught/declared) vs `RuntimeException` (unchecked)
- [ ] `throw new IllegalArgumentException("message")` — used in all value objects for validation
- [ ] Custom exceptions — `InvalidStateTransitionException extends RuntimeException`
- [ ] `try-finally` — used for `MDC.clear()` to prevent thread pool contamination
- [ ] `try-with-resources` — for `Closeable`/`AutoCloseable`; auto-calls `close()` on exit

### Enums — P1
- [ ] Basic enums — `enum PaymentMethod { CARD_CHIP, CONTACTLESS, UPI_QR }`
- [ ] Enums with fields and methods — `TransactionStatus` with valid-transitions map field
- [ ] The transitions map pattern: `Map.ofEntries(entry(AUTHORIZED, Set.of(CAPTURED, REVERSED)))`
- [ ] `switch` on enums — exhaustive with pattern matching switch in Java 21

### Concurrency — P1
- [ ] `CompletableFuture` — async computation: `supplyAsync()`, `thenApply()`, `orTimeout()`, `exceptionally()`
- [ ] Thread safety of records — immutable objects are safe by design; mutable shared state needs synchronization
- [ ] `@Scheduled(fixedDelay = 100)` — Spring scheduler for the outbox relay monitor
- [ ] MDC (Mapped Diagnostic Context) — thread-local log context; must `MDC.clear()` in `finally` for thread pool reuse

---

## 2. Build Tools & Project Structure

### Maven — P1
- [ ] `pom.xml` — Project Object Model; describes project, dependencies, build config
- [ ] Multi-module Maven — parent `pom.xml` with `<modules>`; child poms inherit versions
- [ ] `<dependencyManagement>` — declare versions in parent; children inherit without repeating version numbers
- [ ] `<scope>test</scope>` — dependency only available during tests; not in production JAR
- [ ] Build lifecycle: `compile → test → package → install → deploy`
- [ ] `mvn install` — compiles, tests, installs JAR to local `~/.m2` repo
- [ ] `mvn test -pl domain` — runs tests for one specific module only
- [ ] `-DskipTests` — skip tests during build (use sparingly; only for packaging speed)
- [ ] BOM (Bill of Materials) — Spring Boot BOM manages all compatible dependency versions together

### Maven Plugins — P2
- [ ] `maven-compiler-plugin` — sets Java version (21), enables `--parameters` for Spring
- [ ] `maven-surefire-plugin` — runs unit tests; can exclude `@Tag("integration")`
- [ ] `maven-failsafe-plugin` — runs integration tests in `verify` phase (after package)
- [ ] `jacoco-maven-plugin` — measures code coverage; fails build if below 90%

---

## 3. Architecture Patterns

### Hexagonal Architecture (Ports & Adapters) — P0 🔥
- [ ] Three layers: **Domain Core** (pure Java, zero external imports) → **Application** (use cases, orchestration) → **Adapters** (Spring, JPA, Kafka, Redis, HTTP)
- [ ] **Ports** — interfaces defined by the domain that adapters implement
  - Inbound ports: what the domain exposes — `ProcessPaymentUseCase`
  - Outbound ports: what the domain needs — `TransactionRepository`, `HsmPort`, `FraudPort`
- [ ] **Adapters** — infrastructure implementations: `PostgresTransactionRepository implements TransactionRepository`
- [ ] Why: swap Postgres for MongoDB by writing a new adapter — domain code untouched
- [ ] ArchUnit enforces this at build time — CI fails if domain imports `org.springframework`
- [ ] **Mental model to burn in:** domain defines the contract; adapters fulfill it; never the reverse

### SOLID Principles — P0 🔥
- [ ] **S — Single Responsibility**: `FraudEngine` only scores fraud; it doesn't send webhooks or update status
- [ ] **O — Open/Closed**: add a new payment network by adding a class, not editing `RoutingEngine`
- [ ] **L — Liskov Substitution**: `WireMockNetworkAdapter` and `RazorpayAdapter` are interchangeable; any impl of `NetworkPort` must honor the full contract
- [ ] **I — Interface Segregation**: `AuthorizationPort` is separate from `RefundPort`; don't force implementers to implement methods they don't need
- [ ] **D — Dependency Inversion**: domain depends on `TransactionRepository` interface, not `PostgresTransactionRepository`

### Constructor Injection — P0 🔥
- [ ] Dependencies are `final`; set in constructor; object is immutable after construction
- [ ] Can test with `new MyService(mockRepo, mockKafka)` — no Spring context needed
- [ ] Why NOT `@Autowired` on fields: hidden dependencies; can't use `new` in tests; `final` not possible
- [ ] `Objects.requireNonNull(dependency)` in constructor — fail fast; never silently accept null

### Repository Pattern — P1
- [ ] Domain defines the port interface → adapter implements it → JPA entity is internal to adapter
- [ ] Domain never imports `javax.persistence` or `jakarta.persistence`
- [ ] MapStruct mapper: only bridge between domain records and JPA entities

### Command/Query Separation (CQRS-lite) — P1 🔥
- [ ] Commands change state, return minimal acknowledgement
- [ ] Queries return data, have zero side effects
- [ ] `TransactionRepository` (write) vs `TransactionQueryRepository` (readOnly)
- [ ] `@Transactional(readOnly=true)` routes to Postgres read replica in production

### Domain Events — P1 🔥
- [ ] Domain raises events internally but doesn't publish to Kafka directly
- [ ] `transaction.pullDomainEvents()` — collect events, then publish after DB save succeeds
- [ ] Why: if save fails, there are no ghost events in Kafka

### Transactional Outbox Pattern — P0 🔥
- [ ] Problem: Kafka publish + DB save are two different systems — can't atomically do both
- [ ] Solution: save event to `outbox_events` table **in the same DB transaction** as business data
- [ ] Relay scheduler polls outbox and publishes to Kafka
- [ ] If DB transaction rolls back, event is never published — consistency guaranteed
- [ ] Debezium replaces the polling relay (post-June): reads Postgres WAL instead of polling

### Optimistic Locking — P0 🔥
- [ ] `@Version Long version` on JPA entity
- [ ] Two concurrent updates: first wins, second gets `OptimisticLockException`
- [ ] No DB locks held → high throughput, no deadlocks
- [ ] Must handle the exception: retry or surface as a conflict error

### Value Objects / No Primitive Obsession — P1 🔥
- [ ] Wrap primitives in self-validating types: `MerchantId`, `Money`, `PanHash`
- [ ] Compiler catches argument order swaps — `pay(terminalId, merchantId)` vs `pay(merchantId, terminalId)` are different types
- [ ] Validation at construction — invalid value can never exist in the system

### Event Sourcing — P1 🔥
- [ ] Store events (what happened) instead of current state snapshot
- [ ] `transaction_events` table is the source of truth; not the `transactions` snapshot table
- [ ] Replay all events → reconstruct current state at any point in time
- [ ] Benefits: full audit trail, time-travel debugging, easy read model rebuilding
- [ ] Snapshot optimization: save state at event N; replay from snapshot + events after it
- [ ] Challenge: schema evolution — old events must still be replayable after schema changes

### Full CQRS (Event-Sourced Read Models) — P1 🔥
- [ ] Write side: command → domain service → event stored → outbox
- [ ] Read side: Debezium reads WAL → Kafka → multiple specialized consumers
  - Consumer 1: updates `transaction_dashboard` read model (latest status, amounts)
  - Consumer 2: updates `fraud_analytics` read model (velocity, patterns)
  - Consumer 3: updates `settlement_queue` read model (captured, pending)
- [ ] Read models are denormalized — optimized for their query pattern with no JOINs
- [ ] Eventually consistent: read models lag behind write side by milliseconds
- [ ] Interview framing: "We write events; read models are projections of those events"

### CAP Theorem — P0 🔥
- [ ] Consistency, Availability, Partition Tolerance — can only guarantee 2 of 3 during a network partition
- [ ] Partition tolerance is non-negotiable (networks always fail eventually) — real choice is C vs A
- [ ] **CP** (Consistency + Partition): Postgres for transactions — never serve stale payment data
- [ ] **AP** (Availability + Partition): Redis BIN cache — slightly stale routing data is acceptable
- [ ] Interview framing: "For financial transactions we chose CP; for fraud velocity we chose AP"

### Saga Pattern — P1 🔥
- [ ] Manage distributed transactions across services without 2PC (Two-Phase Commit)
- [ ] **Choreography**: services emit events, others react — loose coupling
  - Used here: `payment.authorized` → webhook-dispatcher consumes → delivers → commits
- [ ] **Orchestration**: central coordinator tells each service what to do
  - Used here: settlement saga — Temporal workflow calls steps in order, retries failures
- [ ] Compensating transactions: if step N fails, undo steps 1..N-1 in reverse order

### Consistent Hashing — P1 🔥
- [ ] Distribute work across N nodes so adding/removing a node remaps only `1/N` keys (not all)
- [ ] Used in: Redis Cluster, Kafka partition assignment, load balancer sticky sessions
- [ ] Alternative to modulo hashing which remaps all keys when N changes
- [ ] Virtual nodes — each physical node occupies multiple ring positions for better balance

---

## 4. Spring Boot

### Core Concepts — P0
- [ ] `@SpringBootApplication` — combines `@Configuration`, `@ComponentScan`, `@EnableAutoConfiguration`
- [ ] Auto-configuration — Spring Boot reads classpath dependencies and registers beans automatically
- [ ] `application.yml` vs `application.properties` — YAML is more readable for nested config
- [ ] Spring profiles — `local`, `dev`, `qa`, `prod` — different config per environment
- [ ] `@Value("${property.name}")` — inject config values; `${ENV_VAR:default}` — env var with fallback

### Bean Wiring — P0
- [ ] `@Component`, `@Service`, `@Repository` — component scanning stereotypes
- [ ] `@Configuration` + `@Bean` — explicit bean definitions; preferred in adapters module
- [ ] `@ConditionalOnProperty` — conditionally register a bean based on config value
  - `hsm.provider: mock` → `MockHsmAdapter`; `hsm.provider: softhsm` → `SoftHsmAdapter`
- [ ] Bean lifecycle — singleton by default; one instance per Spring context

### Spring Data JPA — P0
- [ ] `@Entity`, `@Id`, `@GeneratedValue`, `@Version` — JPA entity annotations
- [ ] `JpaRepository<Entity, ID>` — Spring Data generates SQL from method names
- [ ] `@Query("SELECT t FROM ...")` — custom JPQL for complex queries
- [ ] `@Transactional` — wraps method in a DB transaction; `readOnly=true` routes to replica
- [ ] `@BatchSize(size=25)` — prevents N+1 by batching collection loads into one `IN` query
- [ ] Fetch types: EAGER (load immediately) vs LAZY (load when accessed) — LAZY is the default for collections

### jOOQ — Type-Safe SQL — P1 🔥
- [ ] Generates Java classes from your DB schema — write SQL with type-safety at compile time
- [ ] Why over JPA for analytics: complex JOINs, aggregations, window functions are much cleaner
- [ ] Rule in this project: JPA for simple CRUD; jOOQ for reports and analytics queries
- [ ] `DSLContext.select().from(TRANSACTIONS).where(...).fetch()` — type-safe SQL
- [ ] `sum(TRANSACTIONS.AMOUNT)` — SQL aggregation with Java compile-time type checking

### Spring Data Redis — P1
- [ ] `RedisTemplate` — low-level Redis operations
- [ ] `opsForValue()`, `opsForSet()`, `opsForHash()` — different data structure operations
- [ ] TTL (Time-To-Live) — automatic key expiry: `Duration.ofMinutes(5)`
- [ ] `setIfAbsent()` — Redis SETNX, used for distributed locks and idempotency

### Spring Kafka — P1
- [ ] `@KafkaListener(topics="transaction.events")` — consume messages from topic
- [ ] `KafkaTemplate.send(topic, key, value)` — produce messages
- [ ] Manual offset commit — `enable-auto-commit: false`; commit only after successful processing
- [ ] Why manual commit: if processing fails, event stays in topic for retry

### Spring Batch — P1
- [ ] Steps: Reader → Processor → Writer (chunk-oriented processing)
- [ ] `JpaPagingItemReader` — reads DB in pages; memory-efficient for large datasets
- [ ] Restart from checkpoint — if job fails at step 3, restart resumes at step 3
- [ ] `@Scheduled(cron = "0 30 23 * * *")` — cron expression for daily 23:30 batch run
- [ ] Post-June: Temporal replaces Spring Batch for saga-style distributed workflows

### Spring Actuator — P1
- [ ] `/actuator/health` — reports DB, Redis, Kafka connectivity + custom indicators
- [ ] Liveness vs Readiness probes — Kubernetes uses these for restart vs traffic routing decisions
- [ ] `HealthIndicator` interface — implement for custom checks (HSM, network switch connectivity)
- [ ] `/actuator/prometheus` — Prometheus metrics scrape endpoint

### Spring Security — P1
- [ ] Filter chain — requests pass through ordered security filters before hitting controllers
- [ ] `ApiKeyAuthFilter` — validates `X-Api-Key` header on acquiring REST API
- [ ] mTLS (mutual TLS) — both client and server present certificates; used for network connections

### Spring for GraphQL — P1
- [ ] `@QueryMapping` — maps GraphQL query to a Java method
- [ ] `@MutationMapping` — maps GraphQL mutation
- [ ] `@SubscriptionMapping` — maps GraphQL subscription (real-time via WebSocket)
- [ ] DataLoader / `@BatchMapping` — batch N resolver calls into one query (prevents N+1 in GraphQL)
- [ ] Schema-first: define `.graphqls` schema file, then implement resolver methods

### OpenFeature + Unleash (Feature Flags) — P2
- [ ] Feature flags: toggle features on/off without deploying new code
- [ ] OpenFeature: vendor-neutral SDK — `isEnabled("new-fraud-model", context)`
- [ ] Unleash: self-hosted feature flag server (gradual rollout, A/B test strategies)
- [ ] Used for: enabling ML fraud scoring for 5% of traffic initially before full rollout

---

## 5. Testing

### JUnit 5 — P0
- [ ] `@Test` — mark a method as a test
- [ ] `@ParameterizedTest` + `@MethodSource` / `@CsvSource` — run test with multiple inputs
- [ ] `@BeforeEach`, `@AfterEach` — setup/teardown per test method
- [ ] `@Tag("unit")`, `@Tag("integration")`, `@Tag("chaos")` — categorize and run selectively
- [ ] `assertThrows()` vs `assertThatThrownBy()` — verify exceptions are thrown correctly

### AssertJ — P1
- [ ] `assertThat(value).isEqualTo(expected)` — fluent assertion library; much more readable than JUnit assertions
- [ ] `isEqualByComparingTo()` — BigDecimal comparison that ignores scale differences
- [ ] `isInstanceOf(SomeException.class)` — type check
- [ ] `hasMessageContaining("text")` — check exception message content
- [ ] `containsExactlyInAnyOrder()` — set equality without caring about order

### Mockito — P0
- [ ] `@Mock` — creates a mock (fake) implementation of an interface
- [ ] `when(mock.method()).thenReturn(value)` — stub return values
- [ ] `verify(mock).method(argument)` — assert a method was called with specific arguments
- [ ] `@InjectMocks` — injects `@Mock` fields into the class under test
- [ ] Why: test domain logic in isolation without real DB, Kafka, or Redis

### TDD (Test-Driven Development) — P1 🔥
- [ ] Red → Green → Refactor cycle
- [ ] Write failing test first (doesn't compile yet — that's correct)
- [ ] Write minimum code to make test pass; refactor while keeping tests green
- [ ] Why: forces API design before implementation; produces documentation that never goes stale

### ArchUnit — P1 🔥
- [ ] `@AnalyzeClasses(packages="com.nexswitch")` — scan all classes in package
- [ ] Write architectural rules as regular JUnit tests — violations = test failure = CI failure
- [ ] `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAPackage("org.springframework..")`
- [ ] Prevents accidental architectural erosion as codebase grows

### JaCoCo — P1
- [ ] Instruments bytecode to track which lines were executed during test runs
- [ ] `COVEREDRATIO > 0.90` — build fails if coverage drops below 90%
- [ ] Report at `target/site/jacoco/index.html`
- [ ] Coverage alone is not enough — 90% coverage can still miss logic errors

### Testcontainers — P0 🔥
- [ ] Starts real Docker containers (Postgres, Redis, Kafka) for integration tests
- [ ] `@Container` singleton — one container per JVM, shared across all test classes
- [ ] `@DynamicPropertySource` — injects container ports into Spring config automatically
- [ ] Why: tests against real infrastructure; mocks behave differently from real systems

### WireMock — P1
- [ ] HTTP mock server for testing webhook delivery and external network adapters
- [ ] `stubFor(post(url).willReturn(aResponse().withStatus(200)))` — define stubs
- [ ] `withFixedDelay(20_000)` — simulate timeout scenarios
- [ ] `verify(postRequestedFor(url))` — assert the expected HTTP request was made

### Pact — Consumer-Driven Contract Testing — P2 🔥
- [ ] Problem: integration tests are slow; unit tests don't catch contract breaks between services
- [ ] Consumer (acquiring-service) writes Pact → publishes to Pact Broker
- [ ] Provider (payment-switch) reads Pact from Broker → verifies it still passes
- [ ] Break the contract → CI fails immediately, before any integration tests run

### PIT Mutation Testing — P2 🔥
- [ ] Injects small code changes ("mutants") into production code; runs tests against each mutant
- [ ] Surviving mutants = tests that didn't catch a real bug
- [ ] Stronger than line coverage: 90% coverage can still miss logic errors; PIT proves test quality
- [ ] `mvn org.pitest:pitest-maven:mutationCoverage` — generates HTML report

---

## 6. Databases

### PostgreSQL — P0
- [ ] Relational database — tables, rows, columns, relationships via foreign keys
- [ ] `UUID PRIMARY KEY DEFAULT gen_random_uuid()` — auto-generated unique IDs
- [ ] `NUMERIC(15,2)` — exact decimal storage for money; never `FLOAT` or `DOUBLE`
- [ ] `TIMESTAMPTZ` — timezone-aware timestamps; always use over `TIMESTAMP`
- [ ] `JSONB` — binary JSON column for flexible event data (audit log, outbox payload)
- [ ] Indexes 🔥 — speeds up reads but slows writes; create only for actual query patterns
- [ ] Partial indexes — `WHERE status IN ('CAPTURED', 'PENDING')` — smaller, faster for filtered queries
- [ ] `REVOKE UPDATE, DELETE ON audit_log` — enforce append-only at DB level (PCI-DSS)
- [ ] Connection pools (HikariCP) — reuse connections; creating new connections costs ~5–10ms

### Flyway — P1
- [ ] SQL migration files executed in version order on startup: `V1__create_transactions.sql`
- [ ] Never modify existing migrations — add a new `Vn__` file for any change
- [ ] Flyway tracks executed migrations in `flyway_schema_history` table
- [ ] Only `acquiring-service` has `spring.flyway.enabled: true` — one service owns the schema

### ACID Properties — P0 🔥
- [ ] **Atomicity** — transaction succeeds completely or not at all; no partial updates
- [ ] **Consistency** — DB moves from one valid state to another; constraints enforced
- [ ] **Isolation** — concurrent transactions don't interfere with each other
- [ ] **Durability** — committed data survives crashes (written to WAL before acknowledging)
- [ ] Optimistic locking exploits Isolation without holding DB locks

### Query Optimization — P0 🔥
- [ ] N+1 problem — loading N parents then issuing N separate queries for their children
- [ ] Fix: `JOIN FETCH` in JPQL to load children in same query as parents
- [ ] Fix: `@BatchSize(25)` — load children in batches of 25 with one `IN` query
- [ ] Projections — `SELECT id, amount, status` instead of `SELECT *` — less data transferred
- [ ] `EXPLAIN ANALYZE` — shows query execution plan; "Seq Scan" vs "Index Scan" tells you if index is used

### PgBouncer — Connection Pooling — P1
- [ ] Problem: Postgres handles ~100 connections; 10 service instances × 20 pool each = 200 connections
- [ ] Solution: PgBouncer multiplexes many app connections onto fewer Postgres connections
- [ ] **Transaction mode**: connection returned to pool after each transaction (most efficient)
- [ ] **Session mode**: connection held for lifetime of app connection (some features require this)
- [ ] Config: `pool_size=25`, `max_client_conn=200` — app sees 200 connections, Postgres sees 25

### Elasticsearch — P1 🔥
- [ ] Distributed search engine — full-text search, aggregations, analytics
- [ ] **Index** — collection of documents (like a DB table but schema-flexible)
- [ ] **Document** — JSON record stored in an index
- [ ] **Inverted index** — word → list of documents containing it; enables fast full-text search
- [ ] Used for: transaction search by `merchant_id`, `pan_hash`, `status`, full-text notes
- [ ] Spring Data Elasticsearch — `@Document`, `ElasticsearchRepository`
- [ ] Sync strategy: Debezium → Kafka → Elasticsearch consumer (eventual consistency)

### pgvector — Vector Embeddings — P1 🔥
- [ ] Postgres extension adding a `vector` column type for storing float arrays (embeddings)
- [ ] Used for: storing transaction context embeddings for fraud similarity search
- [ ] **HNSW index** — Hierarchical Navigable Small World — approximate nearest neighbour, fast
  - `CREATE INDEX ON transactions USING hnsw (embedding vector_cosine_ops)`
- [ ] Query: `SELECT * FROM transactions ORDER BY embedding <=> $1 LIMIT 10`
  - `<=>` is cosine distance; `<->` is L2 (Euclidean) distance
- [ ] Why Postgres over Pinecone: one less infrastructure piece; adequate for < 10M vectors

---

## 7. Redis

### Core Concepts — P0
- [ ] In-memory key-value store; sub-millisecond reads; shared across all service instances
- [ ] Data structures: String, Hash, List, Set, Sorted Set — know when to use each
- [ ] TTL (Time-To-Live) — keys auto-expire after set duration; used for sessions and caches
- [ ] Single-threaded execution — all operations are atomic by design

### Patterns Used in This Project — P1
- [ ] Idempotency store — `idempotency:{stan}:{terminalId}:{date}` TTL 24h
- [ ] BIN cache — L2 cache between Caffeine (L1 JVM-local) and Postgres (L3 source of truth)
- [ ] QR session — `qr:session:{txnRef}` TTL 5 minutes
- [ ] Correlation store — `correlation:{arn}:{stan}` TTL 30s; matches async responses to requests
- [ ] Fraud velocity counters — sliding window: `fraud:velocity:pan:{hash}:5min`
- [ ] Distributed lock — `SETNX lock:{key}` with TTL 5s; prevents cache stampede

### Cache Hierarchy — P1 🔥
- [ ] L1 Caffeine — JVM-local, microseconds, top 10k BINs hot
- [ ] L2 Redis — shared across all instances, milliseconds
- [ ] L3 Postgres — source of truth, always correct, ~5ms
- [ ] Cache stampede — many threads miss L2 simultaneously, overwhelm Postgres
- [ ] Prevention: Redis SETNX lock — only one thread fetches from DB, others wait

### Cache Invalidation — P1
- [ ] Event-driven: `merchant.config.updated` Kafka event clears the merchant config cache
- [ ] TTL expiry: passive invalidation; no active management needed
- [ ] Write-through vs write-around: update cache on write vs only populate on read miss

### Redis Sentinel — High Availability — P1
- [ ] Monitors master + replicas; promotes replica on master failure automatically
- [ ] **3-node minimum** for quorum: 1 master + 1 replica + 1 pure sentinel
- [ ] Auto-failover time: ~30 seconds (RTO for Redis)
- [ ] Spring config: `spring.data.redis.sentinel.master: mymaster`
- [ ] Alternative: Redis Cluster (sharding + HA) — overkill for this project's data size

### Bloom Filter — Fast Idempotency — P2 🔥
- [ ] Probabilistic set membership: "definitely NOT in set" or "probably in set" — never false negatives
- [ ] Used for: idempotency fast-path — bloom filter check before Redis → before Postgres UNIQUE
- [ ] `BF.ADD payments:seen:idempotency {key}` / `BF.EXISTS payments:seen:idempotency {key}`
- [ ] Config: 10M capacity, 0.001% false positive rate → ~18MB memory
- [ ] False positive means: occasionally re-check Redis/DB — not a correctness issue
- [ ] Layered: Bloom (O(1) memory) → Redis (O(1) network) → DB UNIQUE (guaranteed)

### Semantic Caching — P2
- [ ] Cache LLM responses by semantic similarity, not exact prompt match
- [ ] Store `(embedding, response)` pairs; on new query: compute embedding → cosine similarity search → if > 0.95 → return cached
- [ ] Why: identical fraud queries phrased differently return same result (saves LLM tokens and cost)

---

## 8. Apache Kafka

### Core Concepts — P0 🔥
- [ ] Distributed event streaming platform — durable, ordered, replayable; not just a message queue
- [ ] **Topic** — named stream of events; **Partition** — topic split for parallelism
  - `transaction.events` has 12 partitions = 12 consumers can work in parallel
- [ ] **Offset** — position of a message in a partition; starts at 0; never decrements
- [ ] **Consumer group** — set of consumers sharing work; each partition assigned to exactly one consumer in the group
- [ ] **Retention** — messages kept 30 days; consumers can replay from any offset at any time
- [ ] Key difference from RabbitMQ: messages aren't deleted on consume; they expire by retention policy

### Producer — P0
- [ ] `acks=all` — all ISR replicas acknowledge before producer considers message sent (no data loss)
- [ ] `enable-idempotence=true` — exactly-once producer semantics (no duplicate sends on retry)
- [ ] `retries=3` — automatic retry on transient network failures

### Consumer — P0
- [ ] `enable-auto-commit=false` — manual offset commit only 🔥
  - Auto-commit can mark a message as processed before processing actually finishes
  - Manual commit: only commit after successful processing → safe retry on failure
- [ ] `auto-offset-reset=earliest` — new consumer group starts from beginning of topic
- [ ] `isolation-level=read_committed` — only read messages from committed transactions (important with Debezium)

### Message Envelope — P1
- [ ] `eventId`, `eventType`, `schemaVersion` — every event carries these fields
- [ ] `schemaVersion` — when you add fields, old consumers still work (backward compatible)
- [ ] `aggregateId` — the transaction UUID this event is about
- [ ] `occurredAt` — when the event happened (domain time, not Kafka ingestion time)

### Schema Registry + Avro — P1 🔥
- [ ] Problem: without schema enforcement, a producer can silently break all consumers
- [ ] Schema Registry: central store; producers/consumers validate schemas on every message
- [ ] Avro: binary serialization (compact, fast); schema stored in Registry, not in each message
- [ ] Schema evolution: BACKWARD (new schema reads old data) vs FORWARD (old schema reads new data)
- [ ] Wire format: `[magic byte][schema ID 4 bytes][avro bytes]` — consumer looks up schema by ID

### Debezium CDC (Change Data Capture) — P1 🔥
- [ ] Problem: outbox relay scheduler polls DB every 100ms — adds latency and unnecessary DB load
- [ ] Solution: Debezium reads Postgres WAL (Write-Ahead Log) and streams changes to Kafka
- [ ] **WAL** — Postgres's internal append-only log of every change; Debezium is a replication client
- [ ] Zero application code changes — Debezium reads the DB directly
- [ ] Latency: milliseconds (vs 100ms+ polling)
- [ ] Debezium event: `{op: "c", before: null, after: {id: ..., status: "AUTHORIZED"}}`
- [ ] Used for: outbox relay, feeding Elasticsearch sync, feeding CQRS read models
- [ ] `publication.autocreate.mode: filtered` — only capture the `outbox_events` table

### Backpressure — P2
- [ ] Problem: producer floods Kafka faster than consumer can process (e.g. during fraud ML slowdown)
- [ ] Solution: consumer pauses partition consumption until processing queue drains
- [ ] `consumer.pause(partitions)` — stops fetching but keeps heartbeat alive (avoids rebalance)
- [ ] `consumer.resume(partitions)` — resume when queue drains below threshold

---

## 9. Security Concepts

### PCI-DSS — P1
- [ ] PAN (Primary Account Number) — 16-digit card number; never stored in plaintext; stored as SHA-256 hash only
- [ ] `PanHash.fromRawPan(pan)` — SHA-256 hash, stored instead of PAN in all DB tables
- [ ] PIN block — encrypted immediately at terminal; never in plaintext outside HSM
- [ ] Audit trail — append-only log required by PCI-DSS; `REVOKE UPDATE, DELETE ON audit_log`

### Cryptography — P1 🔥
- [ ] SHA-256 — one-way hash function; 256-bit output; used to hash PANs for storage
- [ ] HMAC-SHA256 — keyed hash; used for webhook signing
  - `HMAC-SHA256(secret, payload_bytes)` → hex digest
  - Header: `X-Payswiff-Signature: sha256={hex}`
- [ ] AES — symmetric encryption; same key to encrypt and decrypt
- [ ] RSA — asymmetric; public key encrypts, private key decrypts
- [ ] Constant-time comparison — prevents timing attacks on signature validation
- [ ] PKCS#11 — interface standard for HSM (Hardware Security Module) operations

### EMV Chip Security — P2
- [ ] ARQC — Authorization Request Cryptogram; generated by chip per transaction; proves physical card present
- [ ] ARPC — Authorization Response Cryptogram; generated by issuer; card verifies response authenticity
- [ ] ATC — Application Transaction Counter; increments per transaction; prevents replay attacks
- [ ] IMK — Issuer Master Key; diversified per card to derive card-specific session key
- [ ] Key hierarchy: ZMK → ZPK, MAK, DEK → Card Session Key

### PKCS#11 / SoftHSM2 — P2
- [ ] HSM — dedicated crypto hardware; keys never leave the device
- [ ] SoftHSM2 — software implementation for development (same API, no hardware)
- [ ] `SunPKCS11` — Java's PKCS#11 provider
- [ ] Operations: MAC verify, ARQC verify, PIN block translate, ARPC generate

---

## 10. ISO 8583 Protocol

### What It Is — P1
- [ ] Binary protocol for financial transaction messages (cards, ATMs, POS)
- [ ] Used by every bank, card network (Visa, MC, RuPay), and ATM globally
- [ ] jPOS — open-source Java implementation; used in real payment switches worldwide

### Message Structure — P1
- [ ] MTI (Message Type Indicator) — 4 digits identifying message type
  - `0100` = Auth Request, `0110` = Auth Response, `0400` = Reversal, `0800` = Heartbeat
- [ ] Bitmap — 64 or 128 bits; each bit indicates if the corresponding field is present
- [ ] LLVAR — variable length field with 2-digit length prefix
- [ ] LLLVAR — variable length field with 3-digit length prefix

### Key Fields — P1
- [ ] Field 2: PAN (card number)
- [ ] Field 4: Amount (12 digits; implied 2 decimal places)
- [ ] Field 11: STAN (System Trace Audit Number) — 6-digit sequence per terminal; idempotency key
- [ ] Field 38: Authorization Code — 6 alphanumeric (in approved response from issuer)
- [ ] Field 39: Response Code — `00` = approved, `51` = insufficient funds, `05` = do not honor
- [ ] Field 41: Terminal ID (8 chars)
- [ ] Field 42: Merchant ID (15 chars)
- [ ] Field 52: PIN Data (encrypted PIN block)
- [ ] Field 55: ICC/EMV Data (ARQC, ATC, TVR in TLV format)
- [ ] Field 64: MAC (Message Authentication Code) — integrity check

### TCP Transport — P1
- [ ] ISO 8583 runs over raw TCP — not HTTP
- [ ] Persistent connections — one connection handles thousands of transactions
- [ ] Why: lower latency (no TCP handshake per message); required at banking TPS
- [ ] Heartbeat every 30s — MTI 0800 echo to detect dead connections

---

## 11. Observability

### Structured Logging — P1 🔥
- [ ] JSON log output — machine-parseable; queryable in CloudWatch/Loki by field values
- [ ] `logstash-logback-encoder` — converts Logback output to JSON automatically
- [ ] Log levels: ERROR, WARN, INFO, DEBUG — use INFO for business events, DEBUG for diagnostic
- [ ] **Never log**: PAN, PIN, CVV, card track data — PCI violation; career-ending mistake

### MDC (Mapped Diagnostic Context) — P1 🔥
- [ ] Thread-local key-value store attached to every log line in the current thread
- [ ] `MDC.put("transactionId", txn.id().toString())`
- [ ] Why: trace a transaction across hundreds of log lines without grepping by message text
- [ ] `MDC.clear()` in `finally` — thread pool threads reuse; old MDC must be cleared or it leaks into next request

### Prometheus + Metrics — P1
- [ ] Metric types: `Counter` (monotonic, only increases), `Gauge` (can go up or down), `Histogram` (distribution with configurable buckets)
- [ ] `@Timed` — auto-records method execution latency as a histogram
- [ ] `payments_authorization_latency_ms` — histogram; query `p50/p95/p99` in Grafana
- [ ] `payments_transactions_total{status,network}` — counter with labels for filtering
- [ ] Scraped by Prometheus every 15s; stored as time-series data

### Distributed Tracing — P0 🔥
- [ ] **Trace** — one user request spanning multiple services end-to-end
- [ ] **Span** — one unit of work within a trace (a DB query, an HSM call, an HTTP request)
- [ ] Trace ID propagates via HTTP headers (`traceparent`) and Kafka message headers
- [ ] OpenTelemetry — vendor-neutral SDK; auto-instruments Spring Boot with zero code changes
- [ ] **Grafana Tempo** — distributed trace storage and visualization
- [ ] Correlation: trace ID in log line → click in Loki → jump to Tempo trace

### Grafana LGTM Stack — P1 🔥
- [ ] **Loki** — log aggregation (like ELK but cheaper, Grafana-native); indexed by labels only
  - LogQL: `{service="acquiring-service"} |= "ERROR"`
- [ ] **Grafana** — unified visualization: metrics + logs + traces in single pane
  - Click a Prometheus alert → jump to related logs → jump to trace
- [ ] **Tempo** — distributed trace storage; TraceQL queries
- [ ] **Mimir** — horizontally scalable Prometheus for long-term storage
- [ ] **OTel Collector** — receives all OTLP telemetry; routes to Loki + Tempo + Mimir
  - Decouples app from observability backend — swap backends without app changes

### Sentry — Error Tracking — P2
- [ ] Captures exceptions with full stack trace, request context, and user info
- [ ] `Sentry.captureException(e)` — manually report a handled exception with context
- [ ] Release tracking — tag deployments so you can see "this error started in v0.3.0"
- [ ] Alerts: new error, error rate increased 10× in 5 minutes

### SLOs, SLAs, Error Budgets — P0 🔥
- [ ] **SLI** (Service Level Indicator) — the metric you measure (e.g., authorization success rate %)
- [ ] **SLO** (Service Level Objective) — your internal target (99.9% success rate)
- [ ] **SLA** (Service Level Agreement) — contractual promise to customers; stricter than SLO
- [ ] **Error budget** — `1 - SLO = 0.1% = 43.8 minutes/month` of allowed failure
- [ ] **Burn rate** — how fast you're consuming error budget
  - Fast burn: 14× budget consumption in 1 hour → wake-up page alert
  - Slow burn: 1× budget consumption over 6 hours → create a ticket
- [ ] This project's SLOs: auth success ≥ 99.9%, p95 latency < 500ms, webhook delivery ≥ 99.5%

### DORA Metrics — P1 🔥
- [ ] **Deployment Frequency** — how often you deploy to prod (elite: multiple per day)
- [ ] **Lead Time for Changes** — time from commit to prod (elite: < 1 hour)
- [ ] **MTTR** (Mean Time to Restore) — time to recover from incident (elite: < 1 hour)
- [ ] **Change Failure Rate** — % of deployments causing an incident (elite: < 5%)

---

## 12. DevOps & CI/CD

### Docker — P0 🔥
- [ ] Container — isolated process with its own filesystem, network, dependencies; not a VM
- [ ] Image — immutable blueprint for containers; built from `Dockerfile`
- [ ] Multi-stage build — builder stage compiles; final stage has only JRE (smaller, more secure image)
- [ ] `eclipse-temurin:21-jre-alpine` — slim JRE image; no compiler, no debug tools
- [ ] Non-root user — security: `USER payments` in Dockerfile; never run as root
- [ ] `HEALTHCHECK` — Docker monitors container health; restarts unhealthy containers
- [ ] `.dockerignore` — exclude files from build context (like `.gitignore`)

### Docker Compose — P1
- [ ] Defines multi-container local environment in one YAML file
- [ ] `depends_on` + `condition: service_healthy` — waits for Postgres to be ready before starting app
- [ ] `healthcheck` — command Docker runs to test if a container is healthy
- [ ] Volumes — persist data between container restarts (`pgdata:/var/lib/postgresql/data`)
- [ ] Networks — containers talk by service name (`postgres:5432`, `redis:6379`)

### GitHub Actions (CI/CD) — P0 🔥
- [ ] Workflow — YAML file in `.github/workflows/` triggered by events (push, PR, schedule)
- [ ] Job — set of steps running on a runner (e.g., `ubuntu-latest`)
- [ ] Step — individual command or reusable action
- [ ] Matrix strategy — run same job with multiple configurations (Java versions, modules)
- [ ] Secrets — `${{ secrets.AWS_ACCESS_KEY_ID }}` — encrypted; never appear in logs
- [ ] `needs: [test]` — job dependency; test must pass before build runs
- [ ] `environment: prod` with required reviewers — manual approval gate before prod deploy
- [ ] 6-job pipeline: test → integration-test → build-push → deploy-dev → deploy-qa → deploy-prod

### Git Workflow — P1
- [ ] Feature branches — `feat/6-domain-models`, `fix/23-reversal-race`
- [ ] Conventional commits — `feat(#6): add TransactionStatus enum`
- [ ] Squash merge — all PR commits collapsed into one on main; clean linear history
- [ ] `Closes #6` in PR body — auto-closes GitHub issue when PR merges

### AWS Services Used — P1
- [ ] ECS Fargate — run containers without managing underlying servers
- [ ] RDS PostgreSQL — managed Postgres with automated backups and Multi-AZ
- [ ] ElastiCache Redis — managed Redis with Sentinel configuration
- [ ] S3 — object storage for settlement files and reconciliation reports
- [ ] ALB — Application Load Balancer; distributes HTTP traffic to ECS tasks
- [ ] CloudFront — CDN; serves Next.js frontend from edge PoPs
- [ ] Secrets Manager — encrypted storage for DB passwords and API keys
- [ ] CloudWatch — logs, metrics, and alarms
- [ ] IAM — access control via task roles; no hardcoded credentials in code
- [ ] ECR — container image registry; stores built Docker images

---

## 13. Payments Domain Knowledge

### Payment Networks — P0
- [ ] Visa, Mastercard, RuPay (NPCI for domestic cards), UPI (NPCI for UPI payments)
- [ ] **Acquirer** — your bank/payment processor (Payswiff in this project)
- [ ] **Issuer** — cardholder's bank (who actually holds the money)
- [ ] **Network** — Visa/MC/NPCI; routes transactions between acquirer and issuer
- [ ] BIN (Bank Identification Number) — first 6–8 digits of card; identifies issuer; used for routing

### Transaction Lifecycle — P0 🔥
- [ ] **Authorization** — "Is this card valid? Is there enough money?" — places a hold; MTI 0100/0110
- [ ] **Capture** — "Actually take the money" — triggers settlement; MTI 0200
- [ ] Dual-message: auth + separate capture (hotels, e-commerce — auth may differ from final amount)
- [ ] Single-message: auth + capture combined (retail, debit — amount is fixed at time of purchase)
- [ ] **Settlement** — daily batch; acquirer collects from networks; net amounts transferred
- [ ] **Reconciliation** — three-way match: switch log vs network file vs bank statement
- [ ] **Payout** — net amount (after fees, reserves) sent to merchant's bank account

### Reversal vs Refund — P1
- [ ] **Reversal** — before settlement; cancels authorization hold; instant; MTI 0400
- [ ] **Refund** — after settlement; new debit transaction back to cardholder; 3–7 business days; MTI 0200
- [ ] Partial reversal — auth ₹10,000; capture ₹8,000; reverse remaining ₹2,000 hold

### Fraud Prevention — P1
- [ ] Velocity checks — PAN used 3× in 5 minutes → BLOCK (decline locally before hitting network)
- [ ] Risk levels: LOW, MEDIUM, HIGH, BLOCK
- [ ] BLOCK → decline locally without forwarding upstream (saves network costs)
- [ ] Field 44 — fraud flag passed to issuer for HIGH risk transactions
- [ ] Idempotency — same STAN + terminal + date → return cached response; never reprocess

### Chargeback — P2
- [ ] Customer disputes a transaction through their issuing bank
- [ ] Merchant has 30–45 days to submit evidence
- [ ] Evidence: ARQC verified + PIN verified + EMV chip confirmed = merchant wins dispute
- [ ] Reserve account — merchant reserve covers chargeback debits instantly

### MDR (Merchant Discount Rate) — P2
- [ ] Fee the merchant pays on each transaction (typically 1.5–2.5%)
- [ ] Waterfall: gross → interchange (issuer's cut) → network fee → MDR (Payswiff margin) → net to merchant
- [ ] Reserve withholding — 5% of settlement held back; released after settlement cycle completes

---

## 14. TypeScript + Next.js Frontend

### TypeScript Fundamentals — P1 🔥
- [ ] Typed superset of JavaScript; compiles to JS; catches type errors at build time, not runtime
- [ ] `"strict": true` in `tsconfig.json` — enables all strict checks; always use this
- [ ] Basic types: `string`, `number`, `boolean`, `null`, `undefined`, `void`, `never`
- [ ] `interface` vs `type` — interfaces are extendable; types are more powerful (unions, intersections)
- [ ] Generics — `function identity<T>(arg: T): T` — type-safe code that works with multiple types
- [ ] `Record<K, V>`, `Partial<T>`, `Readonly<T>` — utility types; know all three cold
- [ ] `z.infer<typeof schema>` — derive TypeScript type from Zod schema (single source of truth)
- [ ] Discriminated unions — `type Result = { ok: true; data: T } | { ok: false; error: string }`
- [ ] Optional chaining `?.` — use always; non-null assertion `!` — use rarely and with caution

### Next.js App Router — P1 🔥
- [ ] App Router (Next.js 13+) uses `app/` directory and React Server Components by default
- [ ] **Server Components** (default) — rendered on server; can `async/await`; zero JS sent to client; no `useState`/`useEffect`
- [ ] **Client Components** — `'use client'` directive; interactive; event handlers; browser APIs
- [ ] **When Server vs Client**: server for data fetching + initial render; client for interactivity
- [ ] Layouts — `layout.tsx` wraps child routes; persists across navigation (sidebar, header)
- [ ] `loading.tsx` — automatic Suspense boundary for async Server Components (streaming)
- [ ] `error.tsx` — automatic error boundary per route segment
- [ ] API Routes (BFF) — `app/api/*/route.ts` with `GET`, `POST` handlers; server-side only

### Next.js Data Fetching — P1
- [ ] SSR — `fetch(url, { cache: 'no-store' })` — fresh on every request; always up-to-date
- [ ] SSG — `fetch(url)` default `force-cache` — generated at build time
- [ ] ISR — `revalidate: 3600` — static but background-refreshed every N seconds
- [ ] Streaming — `<Suspense>` boundaries; page shell arrives immediately; data chunks follow
- [ ] React Query for client-side data (live dashboard feed, QR status polling)

### State Management — P1
- [ ] React Query v5 — server state; typed with generics: `useQuery<TransactionSummary[]>(...)`
- [ ] Zustand — global UI state; typed store: `create<AppState>()((set) => ({...}))`
- [ ] URL state with **nuqs** — sync filter state to URL query params; shareable filtered URLs
- [ ] Form state: `react-hook-form` + `zod` + `zodResolver`

### Advanced React Patterns — P2
- [ ] Optimistic updates — `onMutate` context for immediate UI update + rollback on failure
- [ ] Virtual scrolling — `@tanstack/virtual` for large lists (thousands of transactions)
- [ ] Code splitting — `React.lazy(() => import('./pages/Simulator'))` + `Suspense`
- [ ] `startTransition` — mark low-priority state updates; keeps UI responsive during filter changes

### shadcn/ui + UX Libraries — P2
- [ ] shadcn/ui — NOT an npm library; copy-paste components built on Radix UI + Tailwind
- [ ] Run `npx shadcn@latest add button` → copies component source into `components/ui/`; you own the code
- [ ] **sonner** — toast notifications: `toast.success("Payment authorized")`
- [ ] **cmdk** — command palette (⌘K) with searchable list of actions
- [ ] **nuqs** — URL search param state; filters survive page refresh and are shareable

### WebSockets (STOMP) — P2
- [ ] WebSocket: bidirectional persistent TCP connection; established via HTTP upgrade
- [ ] STOMP: messaging protocol on top of WebSocket (publish/subscribe to topics)
- [ ] Spring WebSocket: `@MessageMapping("/trigger")` handles client messages
- [ ] Client: `new Client({ brokerURL: 'ws://...' })` → `client.subscribe('/topic/events', handler)`
- [ ] Used for: ISO 8583 event log — bidirectional: trigger a scenario + receive events

### Playwright — E2E Testing — P2 🔥
- [ ] Browser automation testing — Chromium, Firefox, WebKit from one API
- [ ] `page.goto()`, `page.fill()`, `page.click()`, `page.waitForResponse()`
- [ ] `expect(page.locator('.status')).toHaveText('AUTHORIZED')` — wait-and-assert
- [ ] Parallel test execution — each test gets an isolated browser context
- [ ] `playwright.config.ts` — configure base URL, browser, screenshot on failure

---

## 15. gRPC + Protocol Buffers

### Protocol Buffers — P1 🔥
- [ ] Binary serialization format; 5–10× smaller than JSON; 3–5× faster to parse
- [ ] `.proto` file defines service and message types; `protoc` generates Java code
- [ ] Field types: `string`, `int32`, `int64`, `bool`, `bytes`, `repeated` (array), `oneof`
- [ ] `int64` for money in smallest unit (paise) — never `float` or `double` for money

### gRPC in Spring Boot — P1
- [ ] `grpc-spring-boot-starter` — auto-configures gRPC server on port 9090
- [ ] `@GrpcService` on service implementation class
- [ ] `@GrpcClient("fraud-scoring-service")` — injects client stub
- [ ] Interceptors — for auth, logging, distributed tracing (equivalent to HTTP filters)
- [ ] `ManagedChannel` — connection to remote gRPC service; pooled and reused across requests

### gRPC Streaming — P1
- [ ] **Unary**: one request, one response (like REST POST) — most common
- [ ] **Server streaming**: one request, many responses streamed back
  - Used for: streaming fraud reasoning tokens from LLM fraud agent to dashboard
- [ ] **Bidirectional streaming**: continuous exchange on one connection
  - Used for: batch fraud scoring (send N transactions, receive N scores concurrently)

### When to Use gRPC vs REST vs GraphQL — P0 🔥
- [ ] **gRPC**: internal service-to-service, performance-critical, streaming needed
- [ ] **REST**: public APIs, simple CRUD, caching matters (HTTP cache headers work)
- [ ] **GraphQL**: client-driven flexible queries, dashboard with many different widgets

---

## 16. GraphQL

### Core Concepts — P1 🔥
- [ ] Client asks for exactly the data it needs — no over-fetching (REST: you always get all fields)
- [ ] Strongly typed schema — `type Transaction { id: ID!, amount: Float!, status: Status! }`
- [ ] **Query** — read data; **Mutation** — change data; **Subscription** — real-time via WebSocket
- [ ] Single endpoint: `POST /graphql` with `{ query: "...", variables: {} }`
- [ ] `!` means non-null: `amount: Float!` never null; `amount: Float` can be null

### Spring for GraphQL — P1
- [ ] `@QueryMapping`, `@MutationMapping`, `@SubscriptionMapping` (returns `Publisher<T>`)
- [ ] `@SchemaMapping` — resolve a field on a type (e.g., merchant name from merchant ID)
- [ ] DataLoader / `@BatchMapping` — batch N resolver calls into one DB query (solves N+1)
- [ ] Schema-first: write `.graphqls` file first; Spring generates resolver method stubs

---

## 17. Resilience4j — Fault Tolerance

### Why Fault Tolerance — P0 🔥
- [ ] In distributed systems, failures are normal — network timeouts, service crashes
- [ ] Without protection: one slow downstream service cascades into full system failure
- [ ] Resilience4j: library of fault tolerance patterns for Java (replaces Hystrix)

### Circuit Breaker — P0 🔥
- [ ] States: CLOSED (normal, all requests pass) → OPEN (failing, fast-fail immediately) → HALF_OPEN (testing recovery)
- [ ] CLOSED: count failures in sliding window; if failure rate exceeds threshold → OPEN
- [ ] OPEN: immediately reject requests with fallback; no waiting for timeout
- [ ] HALF_OPEN: allow N test requests; success → CLOSED; failure → back to OPEN
- [ ] Config: `failureRateThreshold=50%`, `waitDurationInOpenState=30s`, `slidingWindowSize=10`
- [ ] `@CircuitBreaker(name = "visaNetwork", fallbackMethod = "fallbackRoute")`

### Bulkhead — P1 🔥
- [ ] Isolate resources so one slow consumer doesn't starve others
- [ ] Semaphore bulkhead: limit concurrent calls to N; reject when at limit
- [ ] Each payment network gets its own semaphore — Visa slowdown doesn't affect NPCI
- [ ] Config: `maxConcurrentCalls=10`, `maxWaitDuration=100ms`

### Rate Limiter — P1
- [ ] Limit requests per time window to protect downstream service capacity
- [ ] `@RateLimiter(name = "upstreamNetwork")` — throws `RequestNotPermitted` if exceeded
- [ ] Config: `limitForPeriod=100`, `limitRefreshPeriod=1s`, `timeoutDuration=0s`

### Time Limiter — P1
- [ ] Wraps `CompletableFuture` with a timeout that cancels the future if exceeded
- [ ] `@TimeLimiter(name = "hsmOperation", fallbackMethod = "hsmTimeout")`
- [ ] Config: `timeoutDuration=500ms` for HSM calls; `15s` for upstream network calls

### Retry — P1
- [ ] Auto-retry on transient failures (network blip, 503, timeout)
- [ ] Config: `maxAttempts=3`, `waitDuration=500ms`, exponential backoff with `multiplier=2`
- [ ] Critical: **only retry idempotent operations** — never retry payment capture twice

### Load Shedding — P1 🔥
- [ ] When overloaded, reject low-priority work to protect high-priority authorization path
- [ ] Check `activeRequests > threshold * 0.80`; return `HTTP 503 Service Unavailable` with `Retry-After` header
- [ ] Combined with Resilience4j RateLimiter + Bulkhead for layered protection

---

## 18. Temporal — Workflow Orchestration

### What Temporal Is — P1 🔥
- [ ] Durable execution engine: workflows survive server crashes, restarts, and network failures
- [ ] Key insight: write normal Java code; Temporal replays it automatically on restart from last checkpoint
- [ ] Replaces: Spring Batch jobs, hand-rolled sagas, state machines stored in DB
- [ ] Backed by a workflow history — every step recorded; failure = replay from last checkpoint

### Core Concepts — P1
- [ ] **Workflow** — defines the overall flow; durable, long-lived; `@WorkflowInterface` + `@WorkflowMethod`
- [ ] **Activity** — actual work (DB reads, HTTP calls, Kafka publish); not durable; retried by Temporal
- [ ] **Worker** — polls Temporal server for tasks; executes workflow and activity code
- [ ] **Signal** — send event to a running workflow (e.g., "evidence submitted" → chargeback workflow)
- [ ] **Query** — read workflow state without executing code (e.g., "what step is this settlement on?")

### Why Temporal over Spring Batch — P1
- [ ] Spring Batch: restart logic stored in `BATCH_JOB_EXECUTION` table — complex custom code needed
- [ ] Temporal: state in workflow history — restart is automatic, zero custom code
- [ ] Settlement as Temporal workflow: if step 3 (network submission) crashes → auto-retry from step 3, not step 1

### Saga with Temporal — P1
- [ ] Long-running transactions across services; Temporal orchestrates compensation on failure
- [ ] If any step fails: Temporal runs compensation activities in reverse order automatically

---

## 19. Kubernetes + Helm

### Kubernetes Fundamentals — P0 🔥
- [ ] **Pod** — smallest deployable unit; one or more containers sharing network and storage
- [ ] **Deployment** — manages N pod replicas; rolling updates and rollback
- [ ] **Service** — stable DNS name + load balancing across all pods of a Deployment
- [ ] **Ingress** — HTTP routing from external traffic to internal Services
- [ ] **ConfigMap** — non-secret configuration as key-value pairs mounted as env vars or files
- [ ] **Secret** — base64-encoded sensitive values (DB passwords, API keys)
- [ ] **Namespace** — logical isolation within a cluster: `payments-dev`, `payments-prod`

### Workload Resources — P1 🔥
- [ ] **HPA** (Horizontal Pod Autoscaler) — scale pod count based on CPU/memory/custom metrics
  - `targetCPUUtilizationPercentage: 70` → scale up when CPU > 70%
- [ ] **VPA** (Vertical Pod Autoscaler) — recommend/set CPU + memory requests/limits automatically
- [ ] **PDB** (Pod Disruption Budget) — `minAvailable: 1` keeps pods up during node drain
- [ ] **NetworkPolicy** — pod-level firewall; deny-all default; explicitly allow needed connections

### Key kubectl Commands — P1
- [ ] `kubectl apply -f deployment.yaml` — deploy or update resources from file
- [ ] `kubectl get pods -n payments-dev` — list pods in namespace
- [ ] `kubectl logs -f pod-name` — stream pod logs in real time
- [ ] `kubectl exec -it pod-name -- bash` — open shell into a running pod
- [ ] `kubectl describe pod pod-name` — full event history; probe failures; scheduling issues
- [ ] `kubectl rollout undo deployment/acquiring-service` — rollback to previous version
- [ ] `kubectl port-forward svc/acquiring-service 8080:8080` — expose service locally for debugging

### Helm — P1 🔥
- [ ] Helm chart: directory of Kubernetes YAML templates; `values.yaml` = defaults overridable per environment
- [ ] `helm install`, `helm upgrade --install` (idempotent), `helm rollback`, `helm template` (dry-run)
- [ ] Template syntax: `{{ .Values.image.tag }}` — inject from `values.yaml`
- [ ] Subcharts: one parent chart with acquiring, switch, webhook-dispatcher as subchart dependencies
- [ ] `_helpers.tpl` — shared template fragments for labels, selectors, and annotations

### Local K8s with kind — P2
- [ ] `kind` (Kubernetes IN Docker) — run full K8s cluster locally using Docker containers as nodes
- [ ] `kind create cluster --config kind-config.yaml` — 3 nodes (1 control-plane, 2 workers)
- [ ] Load local image: `kind load docker-image acquiring-service:latest`
- [ ] MetalLB — local load balancer (kind doesn't have a cloud load balancer)

### Probes — P1
- [ ] **Liveness probe** — is container healthy? Fail → kill + restart
  - `httpGet: /actuator/health/liveness` every 30s
- [ ] **Readiness probe** — can container serve traffic? Fail → remove from Service endpoints
  - `httpGet: /actuator/health/readiness` every 10s
- [ ] **Startup probe** — is container done starting? Prevents liveness from killing slow starters
  - `failureThreshold: 30` × `periodSeconds: 10` = 5 minutes to start before liveness kicks in

---

## 20. Infrastructure as Code — Terraform

### Why IaC — P1 🔥
- [ ] Infrastructure defined in code — version controlled, reproducible, reviewable via PR
- [ ] No manual console clicks → no "it works on my AWS account" problems
- [ ] Apply same infra to dev/qa/prod via workspaces with different `tfvars`

### Core Concepts — P1
- [ ] **Provider** — plugin for a cloud API: `hashicorp/aws`, `hashicorp/kubernetes`
- [ ] **Resource** — infrastructure object: `resource "aws_ecs_service" "acquiring" {...}`
- [ ] **Data source** — read existing infra: `data "aws_vpc" "main" { id = var.vpc_id }`
- [ ] **Variable** — input: `variable "environment" { type = string }` — overridden per workspace
- [ ] **Output** — export values: `output "rds_endpoint" { value = aws_db_instance.main.endpoint }`
- [ ] **Module** — reusable group of resources: `modules/ecs-service/` used for all 7 services

### State Management — P1
- [ ] Terraform state: JSON file tracking real infrastructure vs code definition
- [ ] Remote state: S3 bucket + DynamoDB lock table (prevents concurrent applies)
- [ ] `terraform plan` — show what will change; always review before applying
- [ ] `terraform apply` — apply changes; `terraform destroy` — tear down (confirm first)

---

## 21. GitOps — ArgoCD + Argo Rollouts

### GitOps Principles — P1 🔥
- [ ] Git is the single source of truth for what should be running in the cluster
- [ ] No manual `kubectl apply` — all changes go through Git (PR → merge → auto-apply)
- [ ] Drift detection: ArgoCD notices manual changes and auto-reverts them
- [ ] Benefits: full audit trail (who changed what, when), easy rollback (revert the Git commit)

### ArgoCD — P1
- [ ] Watches a Git repo path; merged changes → applies to cluster automatically
- [ ] **Application** — ArgoCD resource: "sync this Git path to this K8s namespace"
- [ ] **App of Apps** pattern: one root Application manages all service Applications
- [ ] **Sync waves** — ordering: `wave: 0` (namespaces, config) before `wave: 1` (deployments) before `wave: 2` (ingress)
- [ ] Health checks: ArgoCD considers Deployment healthy when `availableReplicas == desiredReplicas`

### Argo Rollouts — Canary + Blue-Green — P1 🔥
- [ ] **Canary**: route small % to new version, increase gradually: 5% → 20% → 50% → 100%
- [ ] **AnalysisTemplate** — Prometheus gate: if `success_rate > 0.99` AND `p95 < 500ms` → proceed; else auto-rollback
- [ ] **Blue-Green**: two full deployments; switch 100% traffic instantly; keep old blue for instant rollback
- [ ] Why not K8s rolling update: can't do %-based traffic splitting or auto-rollback on Prometheus metrics

### KEDA — Kubernetes Event-Driven Autoscaling — P1 🔥
- [ ] Autoscale based on external metrics (Kafka lag, Redis queue depth, Prometheus value, cron)
- [ ] Scale-to-zero: KEDA can scale to 0 replicas when no work — K8s HPA minimum is 1
- [ ] Used for: webhook-dispatcher (Kafka lag), fraud-scoring-service (Redis queue), settlement (cron: scale at 23:25)

---

## 22. AI / LLM Engineering

### Large Language Models — P1 🔥
- [ ] LLM: neural network trained on vast text; predicts next token given context window
- [ ] **Context window**: maximum tokens processable in one call (input + output combined)
- [ ] **Token**: roughly a word or word-piece; `"payments" = 1 token`, `"₹6000.00" ≈ 4 tokens`
- [ ] **Temperature**: 0 = deterministic, 1 = creative; fraud scoring uses 0 for consistency
- [ ] **System prompt**: fixed instructions prepended to every conversation
- [ ] Model selection: Haiku for real-time (<50ms budget), Sonnet for batch analysis, Opus for edge cases

### Prompt Engineering — P1 🔥
- [ ] System + user + assistant message structure
- [ ] Few-shot examples in prompt → model mimics the pattern reliably
- [ ] Chain-of-thought: "Think step by step" → better reasoning at cost of more tokens
- [ ] Structured output: "Respond in JSON with keys: risk_level, confidence, reasoning"
- [ ] Prompt injection risk: never concatenate raw user input into prompt without sanitization

### Tool Calling / Function Calling — P1 🔥
- [ ] LLM can call domain functions instead of hallucinating data
- [ ] LLM responds with `tool_use` block → your code executes the function → pass result back → LLM continues reasoning
- [ ] Why: LLM can't access Redis/DB on its own; tool calling bridges domain logic
- [ ] Fraud agent tools: `check_velocity`, `get_geo_distance`, `check_mcc_risk`, `get_merchant_profile`

### Anthropic Prompt Caching — P1 🔥
- [ ] Cache expensive static prefix (fraud rules + transaction schema ≈ 2,000 tokens) across calls
- [ ] First call: write to cache — normal cost; subsequent calls: read from cache — ~10× cheaper
- [ ] Cache TTL: 5 minutes; refresh by re-sending cached content before TTL expires
- [ ] Result: 90% cost reduction on fraud scoring (rules prefix dominates total token count)

### Structured Outputs — P1
- [ ] `tool_choice: "any"` — model must call at least one tool → guaranteed JSON output
- [ ] Why: free-text JSON parsing is fragile; `tool_choice: "any"` is guaranteed structured

### Extended Thinking (Claude) — P2
- [ ] Model reasons internally before responding — like chain-of-thought but not in output
- [ ] `thinking: { type: "enabled", budget_tokens: 1024 }`
- [ ] Used for: ambiguous HIGH-risk transactions where simple fraud rules conflict
- [ ] Cost: adds 1–2 seconds to response — use only for ~5% of edge cases

### Streaming LLM to Frontend — P1 🔥
- [ ] LLM generates tokens one at a time; stream them to frontend as they arrive
- [ ] Backend: `stream=True` → `ReadableStream`; Frontend: `ReadableStream` API + update UI per chunk
- [ ] Used for: fraud reasoning tokens appearing live in Payment Simulator event log

### LangGraph — Stateful Multi-Agent Graph — P1 🔥
- [ ] Stateful, graph-based LLM agents; **Node** = function that processes state; **Edge** = conditional routing
- [ ] **State** — typed dict shared across all nodes (`FraudState` with transaction, scores, reasoning)
- [ ] Fraud agent graph: `RuleBasedNode → VectorSearchNode → [conditional] → LLMReasonNode → AggregatorNode`
- [ ] `StateGraph(FraudState)` → add nodes → add edges → `.compile()` → `.invoke(state)`
- [ ] Why vs simple chain: nodes can loop, branch on intermediate results, share typed state

### pgvector + Embeddings — P1 🔥
- [ ] Embedding: float vector capturing semantic meaning; similar items → similar vectors (close in space)
- [ ] Fraud use case: find past transactions similar to current one → check if they were fraudulent
- [ ] Query: `SELECT * FROM transaction_embeddings ORDER BY embedding <=> $1 LIMIT 5`
- [ ] HNSW index: approximate nearest neighbour at milliseconds latency for millions of vectors
- [ ] `<=>` cosine distance, `<->` L2 distance, `<#>` inner product — choose based on whether vectors are normalised
- [ ] Why Postgres over Pinecone: one fewer service, ACID guarantees, joins with transaction data

### RAG Pipeline — Retrieval-Augmented Generation — P1 🔥
- [ ] Retrieve relevant documents from vector store → inject as context into LLM prompt
- [ ] Fraud use case: 5 most similar historical cases in prompt → LLM classifies current transaction
- [ ] Why RAG over fine-tuning: updated seed data immediately changes model behaviour; no retraining
- [ ] Pipeline: `query → embed → ANN search → [optional rerank] → inject into prompt → LLM response`
- [ ] **Chunk granularity matters**: too coarse → noise injected; too fine → context lost

### Document Chunking — P1 🔥
- [ ] **Fixed-size**: split every N characters/tokens with overlap (simplest; ignores semantics)
- [ ] **Recursive character splitter**: split on `\n\n` → `\n` → `.` → ` ` progressively (LangChain default)
- [ ] **Semantic chunking**: embed each sentence; split where cosine distance between consecutive sentences spikes
- [ ] **Overlap**: repeat last ~20% of previous chunk — prevents context loss at split boundaries
- [ ] Fraud use case: each seed `features_text` is a single atomic fact — no chunking needed (already granular)

### IVFFlat vs HNSW Index — P1 🔥
- [ ] **IVFFlat**: partitions vectors into `lists` clusters; query probes `ivfflat.probes` clusters (default 1)
  - `WITH (lists = 10)` — target `sqrt(row_count)`; run `SET ivfflat.probes = 3` at query time for better recall
  - Lower memory than HNSW; build is faster; acceptable for dev datasets
- [ ] **HNSW**: hierarchical navigable small world graph; near-100% recall at sub-millisecond latency
  - `USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)`
  - `SET hnsw.ef_search = 40` — higher = more accurate, slower; tune per SLA
  - ~2GB memory per 1M 384-dim float32 vectors; switch from IVFFlat at 10k+ rows
- [ ] This project uses IVFFlat (`lists = 10`) — 25 seed rows means the index is an exact scan anyway

### Embedding Model Selection — P1
- [ ] `all-MiniLM-L6-v2` — 384 dims, ~90 MB, ~14k tokens/s on CPU; strong general semantic similarity
- [ ] `text-embedding-3-small` (OpenAI) — 1536 dims (Matryoshka-truncatable to 256), cheap via API
- [ ] `voyage-3-lite` (Anthropic partner) — optimised for retrieval over Claude-generated or finance text
- [ ] Key trade-offs: embedding size vs storage, max input tokens, speed, domain alignment, on-prem vs API
- [ ] **Matryoshka embeddings**: trained so first N dimensions retain most information; truncate to 256 dims = 4× faster query at small recall cost

### Hybrid Search — BM25 + Vector — P2 🔥
- [ ] **BM25** (keyword): scores by term frequency × inverse document frequency; misses synonyms
- [ ] **Vector** (semantic): captures meaning; can miss exact matches ("INR 6000.00" vs "six thousand rupees")
- [ ] **Hybrid**: run both → combine via **Reciprocal Rank Fusion (RRF)**
  - `score = Σ 1 / (k + rank_i)` across result lists; `k = 60` typical; no manual weight tuning
- [ ] Production options: ParadeDB (pgvector + BM25 in one Postgres extension) or pgvector + `pg_bm25`
- [ ] When to use: document retrieval systems; less critical for structured fraud case matching (vectors enough)

### Reranking — Cross-Encoder — P2 🔥
- [ ] ANN retrieval gives top-K candidates fast (bi-encoder); reranker re-scores them with higher accuracy
- [ ] **Bi-encoder**: query and document embedded independently → dot product → O(1) lookup
- [ ] **Cross-encoder**: query + document fed together as one input → much more accurate but O(n) per candidate
- [ ] **Cohere Rerank**: API — send query + top-20 candidates → get reranked list; ~100ms latency
- [ ] **BGE reranker**: `BAAI/bge-reranker-base` — self-hosted; ~500ms for 20 candidates on CPU
- [ ] Pattern: ANN top-20 → reranker → top-5 → inject into prompt; avoids bloating context with noisy retrievals

### Vector Quantization — P2 🔥
- [ ] **Scalar quantization (int8)**: each `float32` → `int8`; 4× smaller, ~1% recall loss
- [ ] **Binary quantization**: each dimension → 1 bit; 32× smaller, higher loss; recover precision with reranking
- [ ] **Product quantization (PQ)**: split vector into M sub-vectors, quantize each independently (FAISS standard)
- [ ] pgvector 0.7+: native `halfvec` (16-bit float) and `bit` (binary) column + index types
- [ ] Trade-off: always benchmark recall@K before and after quantization; int8 is usually safe; binary needs reranking

### LLM Observability + Evals — P2 🔥
- [ ] **Langfuse** — track every LLM call: prompt, response, latency, tokens, cost; self-hosted; no data leaves infra
- [ ] **ragas** — evaluate RAG quality: `context_recall`, `context_precision`, `answer_relevancy`, `faithfulness`
- [ ] **promptfoo** — compare prompt versions; CI gate: fail build if `context_recall < 0.85`
- [ ] Why: without observability you can't improve prompts, debug failures, or track spend

### LiteLLM Gateway — P2 🔥
- [ ] Unified OpenAI-compatible API across providers: Claude primary → GPT-4o-mini fallback → Mistral
- [ ] Same code regardless of provider; transparent fallback on provider failure
- [ ] Budget limits: `max_budget=50 USD/month` — prevents runaway LLM costs

### Context Window Management — P2 🔥
- [ ] Sliding window: keep last N messages + system prompt; drop oldest when window fills
- [ ] Summarization: when approaching limit, summarize old messages into one paragraph before dropping them
- [ ] Why: long fraud investigation chats accumulate history; without management → context truncation → lost reasoning

### MCP Servers (Model Context Protocol) — P2 🔥
- [ ] Protocol exposing domain tools to LLM-powered apps (Claude Desktop, custom agents)
- [ ] `payments-mcp-server`: `get_transaction(id)`, `search_transactions(filter)`, `trigger_reversal(txn_id)`
- [ ] Implementation: FastAPI with MCP endpoints + JSON-RPC protocol

### MLflow + DVC — ML Lifecycle — P2
- [ ] **MLflow**: track experiments (hyperparameters, metrics), register and promote model versions
- [ ] **DVC**: version large ML datasets like Git versions code; `dvc pull` to get exact dataset version

### LoRA Fine-Tuning + vLLM — P2
- [ ] **LoRA**: fine-tune LLM by training small adapter matrices; 1% of full fine-tuning compute cost
- [ ] **QLoRA**: quantize base model to 4-bit → reduce VRAM → fine-tune on cheaper GPU
- [ ] **vLLM**: production LLM inference server; PagedAttention for efficient KV cache; 2–4× faster than Hugging Face

---

## 23. High Availability & Five 9s

### SLO / SLA Fundamentals — P0 🔥
- [ ] 99.9% availability = 8.76 hours/year downtime allowed
- [ ] 99.99% = 52.56 minutes/year; 99.999% (five 9s) = 5.26 minutes/year
- [ ] Error budget = 1 − SLO. At 99.9%: 43.8 minutes/month to "spend" on incidents
- [ ] Error budget burn rate: 14× faster than normal → wake-up page; 1× over 6h → ticket

### Infrastructure High Availability — P1
- [ ] **Multi-AZ RDS**: synchronous standby in different AZ; ~60s failover via DNS switch
  - Write to primary only; standby is not a read replica; `multi_az = true` in Terraform
- [ ] **Redis Sentinel (3-node)**: quorum promotes replica on master failure; ~30s RTO
- [ ] **Kafka RF=3**: each partition on 3 brokers; `min.insync.replicas=2`; survive 2 broker failures
- [ ] **Graceful shutdown**: SIGTERM → drain in-flight requests → SIGKILL after 35s
  - `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 30s`

### RTO and RPO — P1
- [ ] **RTO** (Recovery Time Objective) — max acceptable downtime after failure
  - acquiring-service: RTO 5 min (K8s restarts pod in < 1 min)
  - Postgres: RTO 2 min (Multi-AZ failover)
- [ ] **RPO** (Recovery Point Objective) — max acceptable data loss after failure
  - Postgres: RPO 0 (synchronous Multi-AZ replication)
  - Kafka: RPO 0 (RF=3, acks=all)
  - Redis: RPO seconds (async replica replication)

### Synthetic Monitoring — P2
- [ ] Proactively test from outside (simulates real user); Blackbox Exporter probes HTTP endpoints
- [ ] Alert if `probe_success == 0` for > 2 minutes — catches issues health checks miss

---

## 24. Low Latency Engineering

### ZGC — Z Garbage Collector — P1 🔥
- [ ] GC pauses < 1ms (vs G1GC: 50–200ms pauses) — critical for p99 latency targets
- [ ] Enable: `-XX:+UseZGC -XX:+ZGenerational` in JVM args
- [ ] Used for: acquiring-service + payment-switch (real-time path with p99 targets)
- [ ] Trade-off: slightly higher CPU/memory — acceptable for payment latency requirements

### Async Logging — P2
- [ ] Synchronous Logback writes to disk on every log line — adds latency to request thread
- [ ] `AsyncAppender` — log writes go to in-memory queue; background thread flushes to disk
- [ ] Trade-off: small risk of losing last N log lines on crash — acceptable for latency improvement

### Rate Limiting Algorithms — P1 🔥
- [ ] **Token bucket**: bucket holds N tokens; each request consumes 1; refilled at rate R/sec; allows bursts
  - Used for: per-merchant authorization rate limit
- [ ] **Sliding window log**: store timestamp of each request; count in last N seconds; accurate but memory-intensive
  - Used for: fraud velocity (PAN transactions in last 5 min)
- [ ] **Fixed window counter**: count requests in current window; reset at window end; allows 2× at boundary
  - Used for: daily merchant volume counters
- [ ] **Leaky bucket**: requests drip out at fixed rate regardless of burst; smooth output; adds latency

### Connection Reuse — P1
- [ ] HTTP connection pooling (`HttpClientConnectionManager`) — reuse TCP connections
- [ ] gRPC `ManagedChannel` — one channel per service; HTTP/2 multiplexes many requests over one connection
- [ ] jPOS persistent TCP — one connection per network for all transactions; never per-request
- [ ] HikariCP — DB connection pool; creating new connection costs ~5–10ms; reuse is critical

### Protobuf over JSON — P1
- [ ] Protobuf binary is 5–10× smaller than JSON equivalent
- [ ] Parsing is 3–5× faster than JSON (no string scanning; fixed field offsets)
- [ ] Used for: gRPC messages between acquiring-service and fraud-scoring-service

---

## 25. Modern System Design

### Event Sourcing — P1 🔥
- [ ] Store sequence of events instead of current state snapshot
- [ ] `transaction_events`: every status change is a new row — never UPDATE the transactions table
- [ ] Rebuild state: replay all events for a transaction → reconstruct current state
- [ ] Benefits: full audit trail, time-travel debugging, multiple read models from same events
- [ ] Challenges: schema evolution (old events must still be replayable after schema change)

### Full CQRS — P1 🔥
- [ ] Write path: Command → Domain Service → event in `transaction_events` → Outbox
- [ ] Read path: Debezium reads WAL → Kafka → specialized consumers → denormalized views
  - `dashboard-consumer`: materializes `transactions_dashboard_view` (fast reads, no JOINs)
  - `fraud-analytics-consumer`: materializes `fraud_velocity_view` (aggregations pre-computed)
- [ ] Eventually consistent: milliseconds lag; no JOINs on read side

### Layered Idempotency — P0 🔥
- [ ] Layer 1: Bloom filter in Redis — `BF.EXISTS` is O(1) in-memory; no network hop
- [ ] Layer 2: Redis key — `GET idempotency:{key}` — fast, shared across instances
- [ ] Layer 3: Postgres UNIQUE constraint — `INSERT ... ON CONFLICT DO NOTHING` — guaranteed
- [ ] Layer 4: Optimistic locking (`@Version`) — database-level serialization
- [ ] First layer to catch duplicate = done; subsequent layers are safety nets only

### Database Sharding vs Partitioning — P1 🔥
- [ ] **Horizontal sharding**: split rows across multiple DB instances; shard key = `merchant_id`; cross-shard queries are painful
- [ ] **PostgreSQL table partitioning**: `PARTITION BY RANGE (created_at)` — monthly partitions; still one DB; partition pruning skips irrelevant data
- [ ] This project: no sharding; Postgres table partitioning by date for `transactions`

---

## 26. Security CI/CD Pipeline

### Shift-Left Security — P1 🔥
- [ ] Find security issues during development, not in production
- [ ] Cost: fixing in dev is 10× cheaper than fixing post-prod
- [ ] "Shift left" = move security checks earlier in the SDLC (into dev and CI, not just prod scans)

### Tools in the Pipeline — P2
- [ ] **Trivy** — container image CVE scanning; fails build on CRITICAL vulnerabilities
  - `trivy image acquiring-service:latest --exit-code 1 --severity CRITICAL`
- [ ] **SonarQube** — static code analysis: bugs, security hotspots, coverage gate ≥ 80%
- [ ] **OWASP Dependency-Check** — scan dependencies for known CVEs; fail on CVSS ≥ 8.0
- [ ] **OWASP ZAP** — dynamic API security testing; attacks staging on every deploy
- [ ] **Semgrep** — semantic grep for code patterns (SQL injection, secret leaks, unsafe calls)
- [ ] **detect-secrets** — scans codebase for accidentally committed secrets; `.secrets.baseline` in Git
- [ ] **Dependabot** — weekly automated PRs to update vulnerable dependencies

### Security in Kubernetes — P2
- [ ] NetworkPolicy: deny all by default; explicitly allow only needed connections
- [ ] Pod Security Standards: `runAsNonRoot: true`; read-only root filesystem
- [ ] Secret management: K8s Secrets + External Secrets Operator syncing from AWS Secrets Manager
- [ ] RBAC: ServiceAccount per deployment; minimal permissions; no wildcard access

---

## 27. k6 Load Testing + Chaos Engineering

### k6 Load Testing — P1 🔥
- [ ] Write load tests in JavaScript; k6 runs them with thousands of virtual users
- [ ] Metrics: `http_req_duration` (p95, p99), `http_req_failed`, `iterations/s` (TPS)
- [ ] Performance gate: fail CI if `p95 > 500ms` or `error_rate > 0.1%`
- [ ] Target: 500 TPS with p95 < 500ms — verified with k6 load test before every release

### LitmusChaos — Chaos Engineering — P2 🔥
- [ ] Chaos engineering: intentionally inject failures to prove system resilience
- [ ] Experiments: `pod-delete`, `network-latency`, `pod-cpu-hog`, `node-drain`
- [ ] **Steady-state hypothesis**: define what "healthy" means → inject chaos → verify still healthy
- [ ] Schedule: weekly in staging; quarterly GameDay in production (with approval)

---

## 28. Kong API Gateway

### What an API Gateway Does — P1 🔥
- [ ] Single entry point for all external API traffic; handles cross-cutting concerns at the edge
- [ ] Rate limiting, auth, logging, routing — centralized instead of repeated in every service

### Kong Plugins Used — P2
- [ ] `rate-limiting` — 100 requests/minute per API key
- [ ] `key-auth` — validates `X-Api-Key` header; injects consumer identity into request
- [ ] `prometheus` — exposes Kong metrics (requests, latency, error rate per route)
- [ ] `correlation-id` — injects `X-Correlation-ID` into every request for distributed tracing
- [ ] `CORS` — configure allowed origins for browser-based requests

### Kong vs ALB — P1
- [ ] ALB: L7 routing, SSL termination, health checks — AWS-managed, no plugins
- [ ] Kong: application-layer gateway, plugin ecosystem, custom business logic at the edge
- [ ] Used together: ALB terminates SSL, routes to Kong; Kong handles business-layer concerns

---

## 29. NFC / Contactless Payment Technology

### Contactless EMV — How the Card Side Works — P1 🔥
- [ ] Contactless EMV uses the same ARQC/ARPC cryptography as contact EMV — just over RF
- [ ] Field 22 = 91 (contactless EMV chip, offline PIN not verified) vs 07 (contact chip)
- [ ] CVM (Cardholder Verification Method): amount ≤ ₹5000 = No CVM (CDCVM bypass), amount > ₹5000 = Online PIN even for tap
- [ ] CDCVM (Consumer Device CVM): device biometric (Face ID, fingerprint) replaces PIN entry
- [ ] Acquiring-service flow is identical — the adapter determines Field 22, domain processes it identically

### Tap to Pay on iPhone — P2
- [ ] Apple `ProximityReader` framework — turns iPhone into a software payment terminal
- [ ] Requires: iOS 15.4+, iPhone XS or later (NFC hardware), Apple developer entitlement
- [ ] How it works: ProximityReader presents a contactless interface, reads EMV card data, returns structured `PaymentCardReadResult`
- [ ] No hardware POS terminal needed — phone IS the terminal
- [ ] Use case at portfolio companies: Swiggy/Zomato delivery agents, Zepto runners, small merchants

### Android NFC HCE — P2
- [ ] HCE (Host Card Emulation): Android device emulates a contactless card OR acts as a reader
- [ ] `react-native-nfc-manager` library — JS bridge to `android.nfc` APIs
- [ ] Reader mode: `NfcAdapter.enableReaderMode()` with `FLAG_READER_ISO_DEP`
- [ ] Supports full ISO 14443 contactless EMV read; same acquiring-service flow as hardware terminal

### Apple Watch Companion — P2
- [ ] watchOS app is companion only — no NFC terminal capability on Watch hardware
- [ ] WatchConnectivity framework: bidirectional data transfer between iPhone app and Watch app
- [ ] `WKSession.transferCurrentComplicationUserInfo()` for face complications
- [ ] Push path: APNs → iOS app → WatchConnectivity → watchOS app → haptic notification
- [ ] SwiftUI on watchOS: `WKHostingController`, limited palette but same state model

### What "Contactless" Means in ISO 8583 — P0 🔥
- [ ] Field 22 (POS Entry Mode) drives all behaviour — `91` = contactless EMV, `07` = contact chip
- [ ] CDCVM bit in the EMV data (Field 55) tells issuer whether device biometric was used
- [ ] Issuer can approve CDCVM transactions at any amount — "the phone verified the user"
- [ ] Zero backend changes needed to support NFC — acquiring-service already handles Field 22=91

---

## 30. ISO 20022 — Modern Financial Messaging

### What It Is and Why It Matters — P1 🔥
- [ ] ISO 20022 is an XML/JSON financial messaging standard replacing SWIFT MT messages
- [ ] India's RTGS, NEFT, and Faster Payments have migrated or are migrating to ISO 20022
- [ ] Richer semantic data: structured party info, extended remittance, LEI codes, purpose codes
- [ ] ISO 8583 (card acquiring) coexists with ISO 20022 (interbank/settlement) — they serve different layers

### Key Message Types — P1
- [ ] `pacs.008` — FI to FI Customer Credit Transfer: large-value payment initiation (replaces MT103)
- [ ] `pacs.002` — Payment Status Report: accept/reject/pending status (replaces MT199)
- [ ] `camt.053` — Bank to Customer Statement: end-of-day statement (replaces MT940)
- [ ] `camt.054` — Bank to Customer Debit/Credit Notification: real-time notification (replaces MT942)
- [ ] `camt.056` — FI to FI Payment Cancellation Request: cancel a pacs.008 in flight

### ISO 8583 vs ISO 20022 — Know This for Interviews — P0 🔥
- [ ] ISO 8583: card present transactions, binary protocol, field bitmap, low latency, network-specific
- [ ] ISO 20022: interbank/settlement, XML or JSON, rich semantic model, human-readable, regulatory
- [ ] In this system: ISO 8583 for every card auth; ISO 20022 pacs.008/camt.053 for large-value settlement path
- [ ] Dual-path routing: `if (amount.compareTo(TWO_LAKH) > 0) → ISO 20022 path; else → ISO 8583 path`
- [ ] NPCI, RBI RTGS, NEFT all mandate ISO 20022 now — inevitable for any payments engineer in India

### prowide-iso20022 Library — P2
- [ ] Apache 2.0 open-source library for building and parsing ISO 20022 messages in Java
- [ ] Alternative: JAXB codegen from XSD schemas published by ISO (verbose but zero dependency)
- [ ] `MxPacs00800109` class — pacs.008 message builder + parser, type-safe field access
- [ ] Validates message structure against XSD before sending — schema errors are compile-time

### Where It Lives in This System — P1
- [ ] `LargeValueSettlementPort` in domain — outbound port (interface defined, implemented post-June)
- [ ] settlement-service calls port for transactions > ₹2,00,000 instead of ISO 8583 batch file
- [ ] reconciliation-service: `camt.053` bank statement input replaces MT940 mock file
- [ ] `V10__add_iso20022_tables.sql` migration: `iso20022_messages` table stores raw XML + parsed status

---

# PART TWO — PHASED PREP PLAN

---

## PHASE 1 — FOUNDATION (May 13 → June 30)

**Structure:** 4h afternoon concept study + 3–4h evening project building
**Goal:** Understand every concept deeply enough to implement it and trace it in the codebase

---

### Week 1–2: Java + Architecture (28h study / 42–56h building)

**Study (afternoons):**
- Java 8+ features: lambdas, streams, Optional, method references — write 20 examples minimum
- Java 17–21: records, sealed interfaces, pattern matching switch, virtual threads — build the domain model
- BigDecimal arithmetic — build `Money` record; write 10 edge case tests
- SOLID principles — identify one violation and one correct use of each principle in the project
- Hexagonal architecture — draw all three layers; trace `processPayment()` from HTTP to DB on paper

**Build (evenings):**
- Set up multi-module Maven project structure
- Implement domain model: `Money`, `MerchantId`, `TransactionId`, `PanHash` as records
- Implement `TransactionStatus` enum with valid-transitions map
- Write `TransactionStateMachine` test-first (TDD red-green-refactor)
- Add ArchUnit rule: domain cannot import Spring; verify it fails if violated

**Checkpoint test:** Can you draw hexagonal architecture on a blank page and explain each layer without notes?

---

### Week 3: Spring Boot + Databases (14h study / 21–28h building)

**Study (afternoons):**
- Spring Boot core: auto-config, profiles, `@ConditionalOnProperty`, constructor injection
- Spring Data JPA: `@Entity`, `@Version`, `JpaRepository`, `@Transactional`, N+1 problem
- PostgreSQL: ACID, NUMERIC types, partial indexes, EXPLAIN ANALYZE
- Flyway: migration versioning, never-modify rule
- CQRS-lite: separate read and write repositories

**Build (evenings):**
- Write `V1__create_transactions.sql` through `V3__create_outbox.sql` Flyway migrations
- Build `TransactionJpaEntity` with `@Version` for optimistic locking
- Build `PostgresTransactionRepository` implementing domain `TransactionRepository` port
- Deliberately trigger `OptimisticLockException` in a test — understand what it looks like
- Write a query that N+1s; observe with `show-sql: true`; fix with `JOIN FETCH`

**Checkpoint test:** Can you explain ACID with a concrete payment transaction example?

---

### Week 4: Testing (14h study / 21–28h building)

**Study (afternoons):**
- JUnit 5: `@ParameterizedTest`, `@Tag`, `assertThrows`
- Mockito: mock, stub, verify — understand why you'd mock a port but not a record
- Testcontainers: how `@Container` singleton works; `@DynamicPropertySource`
- TDD: complete one full red-green-refactor cycle for the fraud engine
- WireMock: stubbing, fixed delays, verifying requests

**Build (evenings):**
- Write unit tests for all domain services using Mockito (no Spring context)
- Write integration test for `PostgresTransactionRepository` using Testcontainers (real Postgres)
- Add JaCoCo; drop coverage below 90% deliberately; watch build fail; fix it
- Write WireMock test for webhook delivery with simulated timeout
- Add `maven-surefire-plugin` exclusion of `@Tag("integration")`; add `failsafe` for integration tests

**Checkpoint test:** Can you write a Mockito-based unit test for a domain service from memory?

---

### Week 5: Redis + Kafka (14h study / 21–28h building)

**Study (afternoons):**
- Redis: data structures, TTL, SETNX, cache hierarchy, Sentinel setup
- Kafka: topics, partitions, offsets, consumer groups, manual commit, why retention matters
- Schema Registry + Avro: schema evolution, backward vs forward compatibility
- Transactional outbox pattern end-to-end: problem, solution, how it connects to Kafka

**Build (evenings):**
- Set up Redis: implement idempotency store with TTL; implement distributed lock with SETNX
- Set up Kafka: produce a `transaction.authorized` event; consume it in webhook-dispatcher
- Implement outbox: save to `outbox_events` in same `@Transactional` as payment; build relay scheduler
- Deliberately fail to commit offset; observe message replay on restart
- Write integration tests for outbox pattern using Testcontainers (Postgres + Kafka)

**Checkpoint test:** Can you explain what happens when a Kafka consumer fails mid-processing and why manual commit matters?

---

### Week 6: Observability + Security Basics (14h study / 21–28h building)

**Study (afternoons):**
- MDC: thread-local, transactionId in all log lines, why `finally` clear is mandatory
- Distributed tracing: trace/span, trace ID propagation, OpenTelemetry auto-instrumentation
- Prometheus: metric types (counter, gauge, histogram), `@Timed`, PromQL basics
- SLOs + error budgets: what they are, how to calculate burn rate
- PCI-DSS: PAN hashing, audit log requirements, what never to log

**Build (evenings):**
- Add `logstash-logback-encoder`; verify logs output as JSON
- Add MDC filter: inject `transactionId` and `merchantId` into every request's log context
- Add `micrometer-registry-prometheus`; expose `/actuator/prometheus`; verify metrics appear
- Add Spring Actuator health indicators for DB, Redis, Kafka
- Implement `PanHash.fromRawPan(pan)` with SHA-256; verify PAN never appears in any log

**Checkpoint test:** Can you explain an SLO, an error budget, and what a 14× burn rate means — without notes?

---

### Week 7: Payments Domain + Resilience4j (14h study / 21–28h building)

**Study (afternoons):**
- Payment lifecycle: authorization → capture → settlement → reconciliation → payout
- ISO 8583: MTI structure, bitmap, key fields (F2, F4, F11, F38, F39, F55)
- Reversal vs refund: MTI, timing, partial reversal
- Circuit breaker: three states and transitions; when OPEN is better than waiting
- Bulkhead, time limiter, retry — configuration and when to apply each

**Build (evenings):**
- Implement jPOS integration: parse a real ISO 8583 0100 auth request; build 0110 response
- Add Resilience4j circuit breaker on `VisaNetworkAdapter` and `McNetworkAdapter`
- Trigger OPEN state deliberately by failing 6/10 calls; observe fast-fail behavior in logs
- Configure time limiter for HSM calls (500ms budget); test with a `Thread.sleep(600)` mock
- Add bulkhead: each network adapter gets its own semaphore; observe `BulkheadFullException`

**Checkpoint test:** Can you trace a complete chip card transaction from terminal to issuer response and back?

---

## PHASE 2 — DEPTH (July 1 → September 30)

**Structure:** Afternoons shift to DSA + system design + mock interviews. Project continues evenings only on weekends.

---

### July: DSA Foundation + System Design Basics (4h/day)

**DSA daily routine (2.5h):**
Follow this topic order — don't skip ahead. Each topic should take 1–1.5 weeks:
1. Arrays + two pointers + sliding window
2. HashMaps + frequency counting + anagram problems
3. Linked lists — fast/slow pointers, reversal, merge
4. Binary search — on sorted arrays, on answer space
5. Stacks + monotonic stacks
6. Queues + BFS on grids
7. Binary trees — DFS, BFS, LCA, height, paths

**Target:** 1–2 Leetcode problems per day. Medium is the target. Easy to warm up. Don't attempt Hard yet.

**System design daily routine (1h):**
Read one topic per day from Martin Fowler's blog / System Design Primer. Topics this month:
- CAP theorem (you've built it — now articulate it)
- Consistent hashing with virtual nodes
- Database replication (leader-follower, multi-leader)
- Caching strategies (write-through, write-around, write-back)
- Rate limiting algorithms (token bucket vs sliding window vs leaky bucket)
- Message queues vs event streaming (Kafka vs RabbitMQ)

**Mock interviews (0.5h):** Start with verbal explanations to yourself. Pick one concept daily; explain it out loud as if to an interviewer. Record yourself if possible.

---

### August: DSA Continuation + System Design Practice (4h/day)

**DSA (2h/day):**
8. Trees — BST operations, validation, serialization
9. Graphs — DFS, BFS, topological sort, union-find
10. Heap / Priority Queue — top-K problems, merge K sorted lists
11. Dynamic Programming — 1D DP, knapsack, LCS, coin change
12. Backtracking — subsets, permutations, N-Queens
13. String problems — KMP, sliding window on strings, palindromes

**System design (1h/day):**
Practice designing full systems. Use the 45-minute format:
- Requirements clarification (5 min)
- High-level design (10 min)
- Deep dive one component (20 min)
- Trade-offs + scaling (10 min)

Topics to design this month:
- Design a payment authorization system (you built it — now explain it cleanly)
- Design a URL shortener
- Design a notification system (webhook delivery + retry)
- Design a rate limiter (Redis token bucket)
- Design a distributed cache

**Mock interviews (1h/day from week 2):**
Start structured mocks with a partner or on Pramp/Interviewing.io — 2 per week minimum. The first few will feel terrible. That's expected and necessary.

---

### September: Interview Simulation (4h/day)

**DSA (1.5h/day):**
- Revisit weak topics from July–August
- Mixed practice: random Medium problems across all topics
- Timed sessions: 35 minutes per problem (simulate interview pressure)
- Review solutions even when you get it right — understand the optimal approach

**System design (1.5h/day):**
- Full 45-minute mock designs, 2–3 per week
- Topics: transaction search system, settlement batch system, fraud detection system, real-time event streaming pipeline
- After each: write a one-page critique of your own design

**Mock interviews (1h/day):**
- 2 technical mock interviews per week (DSA + system design alternating)
- 1 behavioral interview practice per week (STAR format; use this project as your primary example)
- Start real applications in late September for roles you're confident about

---

## PHASE 3 — POLISH + APPLY (October → December)

**Goal:** Convert preparation into offers. Don't stop practicing while interviewing.

---

### October: Targeted Applications

- Apply to fintech/payments roles first: Razorpay, PhonePe, CRED, Juspay, BillDesk, PayU, Cashfree
- Apply to strong product companies: Swiggy, Zepto, Groww, Meesho, Atlassian India
- Continue: 1 DSA problem/day, 1 system design mock/week, 1 behavioral mock/week
- Your payments platform project is your primary portfolio piece — be ready to walk through it in 20 minutes

### November–December: Active Interviews + Project Depth

- As real interviews start, use feedback to identify weak areas — drill those specifically
- FAANG India applications: Amazon, Google, Microsoft — apply if DSA feels strong and consistent
- Add project enhancements on weekends: implement Debezium, Temporal, Argo Rollouts, k6 load tests
- These enhancements give you stronger answers to "tell me about a technically challenging problem"

---

## MUST-CODE-FROM-MEMORY (September target)

These are patterns you'll be asked to code or pseudocode in an interview. Practice until automatic:

- [ ] `Money` record with compact constructor validation
- [ ] `TransactionStatus` enum with valid-transitions map and `canTransitionTo()` method
- [ ] Circuit breaker state machine — CLOSED/OPEN/HALF_OPEN with threshold and timeout
- [ ] Layered idempotency — Bloom filter → Redis SETNX → Postgres UNIQUE `ON CONFLICT DO NOTHING`
- [ ] Transactional outbox — save event in same `@Transactional`; relay polls and publishes
- [ ] Optimistic locking — `@Version` field; catch `OptimisticLockException`; retry logic
- [ ] N+1 identification and fix — spot it, fix with `JOIN FETCH` or `@BatchSize`
- [ ] Token bucket rate limiter — `AtomicLong` tokens; scheduled refill; reject when empty
- [ ] Cache hierarchy — L1 Caffeine → L2 Redis → L3 Postgres; SETNX stampede prevention
- [ ] Domain event — raise in domain; `pullDomainEvents()`; publish after DB save

---

## MUST-EXPLAIN-FLUENTLY (September target)

3-minute verbal explanations, no notes, under mild pressure:

- [ ] Why Kafka over a traditional message queue (durability, replayability, consumer groups, retention)
- [ ] CAP theorem with real examples from this project
- [ ] ACID properties with a concrete payment example
- [ ] The outbox pattern: problem → solution → why Debezium improves it
- [ ] Hexagonal architecture: why domain has zero external imports; how to test without Spring
- [ ] Constructor injection: why it beats `@Autowired` on fields for testability
- [ ] Optimistic vs pessimistic locking: when to use each; throughput trade-off
- [ ] Event sourcing: events vs snapshots; audit trail; time-travel debugging; schema evolution risk
- [ ] CQRS: write path (commands + events) vs read path (projections); eventual consistency lag
- [ ] Saga: choreography vs orchestration; compensating transactions; why not 2PC
- [ ] Circuit breaker: three states; why fast-fail beats waiting; how HALF_OPEN tests recovery
- [ ] SLOs + error budgets: how they change the conversation from alerts to budget management
- [ ] Consistent hashing: why modulo breaks with N changes; virtual nodes solve balance
- [ ] Rate limiting algorithms: token bucket vs sliding window vs leaky bucket; when to use each

---

## SYSTEM DESIGN PRACTICE SCENARIOS

Practice designing these end-to-end in 45-minute timed sessions:

- [ ] Design a payment authorization system (500 TPS, idempotency, fraud check, state machine)
- [ ] Design a webhook delivery system (at-least-once, retry with backoff, merchant SLA)
- [ ] Design a distributed rate limiter (per-merchant, Redis token bucket, multi-region)
- [ ] Design a transaction search system (CQRS, Debezium, Elasticsearch, eventual consistency)
- [ ] Design a settlement batch system (Temporal workflow, idempotent steps, partial failure, reconciliation)
- [ ] Design a real-time fraud detection system (rule engine → vector similarity → LLM; sub-100ms)
- [ ] Design a notification system (Kafka, fan-out, multiple channels, deduplication)
- [ ] Design a URL shortener (consistent hashing, caching, analytics)
- [ ] Design a distributed cache (eviction policies, cache invalidation, stampede prevention)
- [ ] Design a ride-sharing backend (location tracking, matching, trip state machine)

---

# PART THREE — RESOURCES

---

## Java + Spring Boot

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [Baeldung](https://www.baeldung.com) | 🆓 | P0 ⭐ | Best free Spring/Java articles; search any topic + "baeldung" |
| [Spring Official Guides](https://spring.io/guides) | 🆓 | P0 | Short hands-on tutorials for every Spring module |
| [Spring Boot Reference Docs](https://docs.spring.io/spring-boot/docs/current/reference/html/) | 🆓 | P1 | Authoritative reference; search when stuck |
| *Effective Java* — Joshua Bloch | 📖💰 | P0 ⭐ | Best Java book; read items 1–50 minimum |
| [Java 21 JEPs (openjdk.org)](https://openjdk.org/projects/jdk/21/) | 🆓 | P1 | Official JEPs for records, sealed interfaces, virtual threads |
| [Baeldung — Java 21 Features](https://www.baeldung.com/java-21-new-features) | 🆓 | P0 ⭐ | Practical examples of every Java 21 feature used in this project |
| [jOOQ Getting Started](https://www.jooq.org/doc/latest/manual/getting-started/) | 🆓 | P1 | Official jOOQ docs; start with "Tutorial" section |

---

## Architecture + System Design

| Resource | Type | Priority | Notes |
|---|---|---|---|
| *Designing Data-Intensive Applications* — Kleppmann | 📖💰 | P0 ⭐ | **The** distributed systems book; covers Kafka, DBs, replication, CAP, CQRS |
| *Implementing Domain-Driven Design* — Vernon | 📖💰 | P0 ⭐ | Deep dive on hexagonal arch, aggregates, domain events, bounded contexts |
| *Clean Architecture* — Robert C. Martin | 📖💰 | P1 | Ports & adapters explained clearly; read after Vernon |
| [Martin Fowler's Blog](https://martinfowler.com) | 🆓 | P0 ⭐ | Canonical articles on CQRS, Event Sourcing, Saga, Outbox — read these before watching videos |
| [System Design Primer (GitHub)](https://github.com/donnemartin/system-design-primer) | 🆓 | P0 ⭐ | 200k+ stars; covers every interview topic with diagrams |
| [ByteByteGo Newsletter + YouTube](https://bytebytego.com) | 🆓/💰 | P0 ⭐ | Alex Xu's visual system design explanations; free YouTube is excellent |

---

## Testing

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/) | 🆓 | P0 | All annotations, parameterized tests, extensions |
| [Mockito Documentation](https://site.mockito.org) | 🆓 | P0 | Start with "How to write good tests" section |
| [Testcontainers Guides](https://testcontainers.com/guides/) | 🆓 | P0 ⭐ | Step-by-step with Postgres, Kafka, Redis and Spring Boot |
| [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html) | 🆓 | P1 | All rule types with examples; read "Concepts" chapter first |
| [WireMock Docs](https://wiremock.org/docs/) | 🆓 | P1 | Stubs, scenarios, fixed delays |
| [Pact Docs](https://docs.pact.io) | 🆓 | P2 | Start with "How Pact works" |
| [PIT Mutation Testing](https://pitest.org/quickstart/maven/) | 🆓 | P2 | Maven quickstart; understand mutation score vs line coverage |

---

## Databases + Caching

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [PostgreSQL Documentation](https://www.postgresql.org/docs/current/) | 🆓 | P0 ⭐ | Best DB docs anywhere; read chapters on indexes, EXPLAIN, transactions |
| [Use the Index, Luke](https://use-the-index-luke.com) | 🆓 | P0 ⭐ | Free book on SQL indexing; partial indexes, EXPLAIN ANALYZE |
| [Flyway Documentation](https://documentation.red-gate.com/flyway) | 🆓 | P1 | Migrations, versioning, repair |
| [PgBouncer Docs](https://www.pgbouncer.org/config.html) | 🆓 | P1 | Config reference; transaction vs session mode explained |
| [Redis Documentation](https://redis.io/docs/) | 🆓 | P0 | Data structures, TTL, pub/sub, persistence |
| [Redis University](https://university.redis.com) | 🆓 | P0 ⭐ | Free courses: RU101 (Intro), RU203 (Streams), RU330 (Security) |
| [pgvector GitHub](https://github.com/pgvector/pgvector) | 🆓 | P1 | README covers HNSW vs IVFFlat, cosine vs L2, indexing |
| [Elasticsearch Guide](https://www.elastic.co/guide/en/elasticsearch/reference/current/) | 🆓 | P1 | Start with "Getting started" then inverted index concept |

---

## Kafka + Messaging

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [Kafka: The Definitive Guide](https://www.confluent.io/resources/kafka-the-definitive-guide-v2/) | 🆓 | P0 ⭐ | Free PDF from Confluent; chapters 1–6 are required reading |
| [Confluent Developer Tutorials](https://developer.confluent.io/learn-kafka/) | 🆓 | P0 ⭐ | Hands-on Kafka courses; "Kafka for Spring" is directly relevant |
| [Debezium Documentation](https://debezium.io/documentation/reference/stable/) | 🆓 | P1 | CDC docs; start with Tutorial then Postgres connector config |
| [Confluent Schema Registry Docs](https://docs.confluent.io/platform/current/schema-registry/) | 🆓 | P1 | Schema evolution rules, Avro integration |
| [Apache Avro Spec](https://avro.apache.org/docs/current/specification/) | 🆓 | P1 | Schema syntax, field types, default values, evolution rules |

---

## Resilience + Fault Tolerance

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [Resilience4j Docs](https://resilience4j.readme.io/docs) | 🆓 | P0 ⭐ | All patterns with Spring Boot config examples |
| [Temporal Documentation](https://docs.temporal.io) | 🆓 | P1 ⭐ | Start with "Core concepts" then Java SDK tutorial |
| *Release It!* — Michael Nygard | 📖💰 | P1 | Original circuit breaker book; stability patterns in production |

---

## Observability

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [Grafana Documentation](https://grafana.com/docs/) | 🆓 | P1 | Covers Grafana, Loki, Tempo, Mimir — unified docs site |
| [Grafana Tutorials](https://grafana.com/tutorials/) | 🆓 | P1 ⭐ | "Getting started with Grafana and Prometheus" is the right start |
| [OpenTelemetry Docs](https://opentelemetry.io/docs/) | 🆓 | P1 | Java auto-instrumentation, collector config |
| [Prometheus Docs](https://prometheus.io/docs/introduction/overview/) | 🆓 | P1 | PromQL, metric types, alerting rules |
| [Google SRE Book](https://sre.google/sre-book/table-of-contents/) | 🆓 | P0 ⭐ | Free online; read chapters 2–6 on SLOs, error budgets, toil |

---

## DevOps: Docker + Kubernetes + Helm

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [Docker Get Started](https://docs.docker.com/get-started/) | 🆓 | P0 | Multi-stage builds in Part 9 |
| [Play with Docker](https://labs.play-with-docker.com) | 🆓 | P0 | Free browser-based Docker playground |
| [Kubernetes Official Docs](https://kubernetes.io/docs/home/) | 🆓 | P0 ⭐ | Best K8s reference; "Concepts" section is required reading |
| *Kubernetes in Action* — Marko Luksa | 📖💰 | P1 ⭐ | Best K8s book; builds from pods up to production patterns |
| [KodeKloud K8s Course](https://kodekloud.com/courses/kubernetes-for-the-absolute-beginner-hands-on/) | 💰 | P1 | Cheap; best hands-on intro with browser labs |
| [Helm Docs](https://helm.sh/docs/) | 🆓 | P1 | Chart template reference; start with "Getting Started" |
| [ArgoCD Documentation](https://argo-cd.readthedocs.io/en/stable/) | 🆓 | P1 | App of Apps, sync waves, RBAC |
| [Argo Rollouts Docs](https://argoproj.github.io/rollouts/) | 🆓 | P1 | Canary + blue-green strategies, AnalysisTemplate |
| [KEDA Documentation](https://keda.sh/docs/) | 🆓 | P1 | Scalers: Kafka, Redis, Prometheus, cron |

---

## Infrastructure as Code

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [HashiCorp Terraform Tutorials](https://developer.hashicorp.com/terraform/tutorials) | 🆓 | P1 ⭐ | Official interactive tutorials; "AWS Get Started" is the right entry point |
| *Terraform: Up & Running* — Brikman | 📖💰 | P1 | Best Terraform book; modules, state, workspaces |

---

## APIs: gRPC + GraphQL

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [gRPC Official Docs](https://grpc.io/docs/) | 🆓 | P1 ⭐ | Start with "Introduction to gRPC" then Java quickstart |
| [Protocol Buffers Docs](https://protobuf.dev/getting-started/) | 🆓 | P1 | Protobuf syntax, field types, evolution rules |
| [Baeldung — gRPC with Spring](https://www.baeldung.com/grpc-introduction) | 🆓 | P1 | Practical Spring Boot gRPC setup |
| [How to GraphQL](https://www.howtographql.com) | 🆓 | P1 ⭐ | Free full-stack tutorial; schema, resolvers, subscriptions |
| [GraphQL Official Docs](https://graphql.org/learn/) | 🆓 | P1 | Spec + learn section; good for understanding the "why" |
| [Baeldung — Spring for GraphQL](https://www.baeldung.com/spring-graphql) | 🆓 | P1 | @QueryMapping, DataLoader, subscriptions |

---

## TypeScript + Next.js Frontend

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [TypeScript Handbook](https://www.typescriptlang.org/docs/handbook/intro.html) | 🆓 | P1 ⭐ | Official; read cover to cover — generics and utility types are key |
| [Total TypeScript](https://www.totaltypescript.com) — Matt Pocock | 🆓/💰 | P2 | Free articles + paid workshops; best advanced TypeScript resource |
| [Next.js Documentation](https://nextjs.org/docs) | 🆓 | P1 ⭐ | Official; App Router section is the focus |
| [Next.js Learn Course](https://nextjs.org/learn) | 🆓 | P1 | Official interactive course; builds a dashboard app similar to this project |
| [TanStack Query Docs](https://tanstack.com/query/latest) | 🆓 | P1 | React Query v5; guides + API reference |
| [Zustand Docs](https://zustand.docs.pmnd.rs) | 🆓 | P1 | Short docs; read in 30 minutes; TypeScript section is important |
| [shadcn/ui Docs](https://ui.shadcn.com/docs) | 🆓 | P2 | Component installation; Theming section for Tailwind integration |
| [Playwright Docs](https://playwright.dev/docs/intro) | 🆓 | P2 | "Getting Started" + "Best Practices" |
| [Tailwind CSS Docs](https://tailwindcss.com/docs) | 🆓 | P2 | Scan once; bookmark; search as needed |

---

## AI / LLM Engineering

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [Anthropic Documentation](https://docs.anthropic.com) | 🆓 | P1 ⭐ | API reference, prompt engineering guide, tool use, streaming, prompt caching |
| [Anthropic Prompt Engineering Guide](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/overview) | 🆓 | P1 ⭐ | Few-shot, CoT, XML tags, system prompts — official guide |
| [DeepLearning.AI Short Courses](https://www.deeplearning.ai/short-courses/) | 🆓 | P1 ⭐ | Free; "LangChain for LLM App Dev", "Building Systems with ChatGPT API", "AI Agents" |
| [LangGraph Docs](https://langchain-ai.github.io/langgraph/) | 🆓 | P1 | Conceptual guide + Python tutorials; start with "Why LangGraph?" |
| [Langfuse Documentation](https://langfuse.com/docs) | 🆓 | P2 | LLM tracing, evals, prompt management; self-hosting guide |
| [ragas Documentation](https://docs.ragas.io) | 🆓 | P2 | RAG evaluation metrics; quickstart |
| [promptfoo Documentation](https://www.promptfoo.dev/docs/intro) | 🆓 | P2 | Prompt regression testing; CI integration |
| [vLLM Documentation](https://docs.vllm.ai/en/latest/) | 🆓 | P2 | Serving fine-tuned models; OpenAI-compatible API |
| [Hugging Face PEFT Docs](https://huggingface.co/docs/peft) | 🆓 | P2 | LoRA/QLoRA implementation; practical fine-tuning guide |
| [MLflow Documentation](https://mlflow.org/docs/latest/index.html) | 🆓 | P2 | Experiment tracking + model registry |
| [DVC Documentation](https://dvc.org/doc/start) | 🆓 | P2 | Data versioning + pipeline stages |

---

## Payments Domain

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [jPOS Programmer's Guide](http://jpos.org/doc/proguide.pdf) | 🆓 | P1 ⭐ | Free PDF; definitive jPOS reference for ISO 8583 in Java |
| [EMVCo Specifications](https://www.emvco.com/emv-technologies/contact/) | 🆓 | P2 | Official EMV specs (free registration); Book 2 covers ARQC/ARPC |
| [NPCI UPI Ecosystem](https://www.npci.org.in/what-we-do/upi/product-overview) | 🆓 | P1 | UPI product overview and ecosystem documentation |
| [ISO 8583 Wikipedia](https://en.wikipedia.org/wiki/ISO_8583) | 🆓 | P1 | Good overview of MTIs, bitmaps, field definitions — start here |
| *Payments Systems in the U.S.* — Carol Coye Benson | 📖💰 | P2 | US-centric but foundational; authorization, clearing, settlement explained |

---

## Security

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [OWASP Top 10](https://owasp.org/www-project-top-ten/) | 🆓 | P1 ⭐ | 10 most critical web security risks with mitigations |
| [OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org) | 🆓 | P1 | Authentication, HMAC, SQL injection prevention — quick reference |
| [Trivy Documentation](https://aquasecurity.github.io/trivy/) | 🆓 | P2 | Container + filesystem scanning; CI integration guide |
| [SonarQube Docs](https://docs.sonarsource.com/sonarqube/latest/) | 🆓 | P2 | Quality gates, security hotspots, Maven plugin config |
| [detect-secrets GitHub](https://github.com/Yelp/detect-secrets) | 🆓 | P2 | Usage + baseline setup; CI integration in README |

---

## Load Testing + Chaos

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [k6 Documentation](https://grafana.com/docs/k6/latest/) | 🆓 | P1 ⭐ | Scripting, metrics, thresholds, CI integration |
| [LitmusChaos Documentation](https://docs.litmuschaos.io) | 🆓 | P2 | Experiments catalog; ChaosEngine CRD |
| *Chaos Engineering* — Casey Rosenthal | 📖💰 | P2 | Principles from Netflix chaos engineers |

---

## YouTube Channels

| Channel | Priority | What it covers |
|---|---|---|
| [ByteByteGo](https://www.youtube.com/@ByteByteGo) | P0 ⭐ | System design visuals — Kafka, Redis, consistent hashing, rate limiting |
| [TechWorld with Nana](https://www.youtube.com/@TechWorldwithNana) | P1 ⭐ | K8s, Docker, ArgoCD, Terraform — best hands-on intros |
| [Confluent](https://www.youtube.com/@Confluent) | P1 | Kafka deep dives, Debezium, Schema Registry talks from the creators |
| [Anton Putra](https://www.youtube.com/@AntonPutra) | P2 | Kubernetes, Prometheus, Grafana, ArgoCD hands-on |
| [Fireship](https://www.youtube.com/@Fireship) | P1 | TypeScript, Next.js, AI — fast 100-second overviews |
| [Theo (t3.gg)](https://www.youtube.com/@t3dotgg) | P2 | TypeScript, Next.js, React ecosystem |
| [Matt Pocock](https://www.youtube.com/@mattpocockuk) | P2 | Advanced TypeScript patterns |
| [Anthropic on YouTube](https://www.youtube.com/@anthropic-ai) | P1 | Claude capabilities, prompt engineering, AI safety |

---

## Interactive Platforms

| Platform | Priority | Best for |
|---|---|---|
| [Killercoda](https://killercoda.com) | P1 ⭐ | Free browser-based K8s, Docker, Linux labs — no local setup required |
| [Play with Kubernetes](https://labs.play-with-k8s.com) | P1 | Free 4-hour K8s playground in browser |
| [Grafana Play](https://play.grafana.org) | P1 | Live Grafana instance with demo dashboards — explore without installing |
| [Confluent Cloud Free Tier](https://www.confluent.io/confluent-cloud/tryfree/) | P1 | Managed Kafka free tier — good for Schema Registry practice |
| [HashiCorp Instruqt](https://developer.hashicorp.com/terraform/tutorials) | P2 | Interactive Terraform tutorials in browser |
| [Pramp](https://www.pramp.com) | P0 ⭐ | Free peer mock interviews — start using from July |
| [Interviewing.io](https://interviewing.io) | P1 | Anonymous mock interviews with engineers from top companies |
| [Leetcode](https://leetcode.com) | P0 ⭐ | DSA practice — start July; target 150–200 problems by September |

---

## DSA Resources (July onwards)

| Resource | Type | Priority | Notes |
|---|---|---|---|
| [Neetcode 150](https://neetcode.io/practice) | 🆓 | P0 ⭐ | Curated 150 problems by pattern; best structured DSA prep |
| [Neetcode YouTube](https://www.youtube.com/@NeetCode) | 🆓 | P0 ⭐ | Video explanations for every problem; crystal clear |
| [Leetcode](https://leetcode.com) | 🆓/💰 | P0 | Practice platform; company-specific problem sets |
| *Cracking the Coding Interview* — McDowell | 📖💰 | P1 | Good behavioral chapters; DSA explanations decent |
| [CP-Algorithms](https://cp-algorithms.com) | 🆓 | P2 | Deep algorithmic theory; use for graph and DP deep dives |

---

# APPENDIX — ONE-PAGE PROBABILITY RECAP

```
YOUR SETUP:
  May–June:    7–8h/day (4h study + 3–4h project)    ~1,000h by June 30
  July–Sep:    4–5h/day (DSA + sys design + mocks)    ~500h by Sep 30
  Oct–Dec:     4h/day + real interviews                ~400h by Dec 31
  TOTAL:       ~1,900h across 7.5 months

PROBABILITY OF CLEARING TECHNICAL ROUNDS:

  By Sep 30:
    Fintech/Payments (Razorpay, PhonePe, CRED, Juspay)   85–90%
    Strong Product Co. (Swiggy, Groww, Zepto, Meesho)     75–80%
    FAANG India (Google, Amazon, Microsoft)                60–65%

  By Dec 31:
    Fintech/Payments                                       92–95%
    Strong Product Co.                                     85–90%
    FAANG India                                            72–78%

WHAT DETERMINES WHETHER YOU HIT THE HIGH OR LOW END:
  HIGH: consistent mock interviews from July, active coding > passive reading,
        strong DSA (150+ problems across all patterns), can articulate trade-offs fluently
  LOW:  passive learning, skipping mocks until "ready", DSA left as afterthought,
        starting real interviews before Sep without enough reps
```

---

---

## §31 — Systems Programming: Networking, OS, and Kernel Internals

*Taught by building the Netty TCP server (#17), Resilience4j bulkhead (#34), and ZGC tuning (#68).*
*Look for `// LEARN:` comments in those implementations for the exact concept in context.*

### 31.1 TCP Connections and Ports

| Concept | What it means | Where you see it |
|---|---|---|
| TCP 4-tuple | A connection = (src-ip, src-port, dst-ip, dst-port). One server port handles millions of connections — the port number is NOT the limit | Netty TCP server in acquiring-service |
| File descriptors | Each socket = one FD. Default `ulimit -n` is 1024. Production servers set it to 1,000,000+ | `lsof` output climbing during load test |
| SYN queue | Kernel holds half-open connections (SYN received, SYN-ACK sent, waiting for ACK). Size = `tcp_max_syn_backlog` | SYN flood attack exhausts this |
| Accept queue | Fully established connections waiting for `accept()` syscall. Size = `listen(fd, backlog)`. If full, kernel drops new connections | `netstat -s` shows overflows |
| `SO_REUSEPORT` | Multiple threads/processes bind the same port. Kernel round-robins incoming connections across them — eliminates single `accept()` bottleneck | Nginx workers, Netty multi-thread boss group |
| TIME_WAIT | After connection close, socket stays in TIME_WAIT for 2×MSL (~60s) to catch delayed packets. Exhausts ephemeral ports under high churn | `SO_REUSEADDR` mitigates on server side |

### 31.2 Non-Blocking I/O and the Reactor Pattern

| Concept | What it means | Where you see it |
|---|---|---|
| Blocking I/O | Thread blocks on `read()`/`write()` until data arrives. Thread-per-connection = 1 thread per socket = doesn't scale | Old servlet containers (pre-NIO) |
| Non-blocking I/O | `read()` returns immediately with EAGAIN if no data. Application polls or waits for event notification | Java NIO, Netty |
| `select` / `poll` | Kernel notifies which FDs are ready. O(n) scan — degrades with thousands of FDs | Legacy; replaced by epoll |
| `epoll` (Linux) / `kqueue` (macOS) | O(1) event notification — kernel maintains a ready list, only returns FDs with events | Netty uses this internally |
| Reactor pattern | One event loop thread demultiplexes I/O events and dispatches to handlers. Netty's `EventLoop` is a reactor | acquiring-service TCP server |
| C10K problem | "Can a server handle 10,000 concurrent connections?" — solved by epoll + non-blocking I/O | Netty handles C1M routinely |

### 31.3 Kernel Round-Robin and Load Distribution

| Concept | What it means | Where you see it |
|---|---|---|
| `SO_REUSEPORT` round-robin | Kernel hashes (src-ip, src-port) to pick which listening socket gets the connection — consistent for a given client | Netty boss thread group |
| L4 load balancing | TCP-level: load balancer forwards packets without terminating the TCP connection. Faster, less CPU | AWS NLB, Kubernetes Service |
| L7 load balancing | HTTP-level: terminates TLS, reads HTTP headers, routes by path/host. Slower, more features | AWS ALB, Kong, Nginx |
| Kernel CFS scheduler | Completely Fair Scheduler divides CPU time in O(log n) via red-black tree. Each thread gets a time slice | Context switching cost under high thread count |
| Context switch cost | Saving/restoring registers + flushing TLB = ~1–10μs. 10,000 threads = millions of μs/s wasted | Why event loops beat thread-per-connection |

### 31.4 Memory and System Calls

| Concept | What it means | Where you see it |
|---|---|---|
| Virtual memory | Each process sees its own address space. Kernel maps virtual → physical pages. Swap = page written to disk | JVM heap lives in virtual memory |
| Page fault | Access to unmapped virtual page — kernel allocates physical page. Minor (page in memory) vs major (page on disk) | JVM startup, large heap allocation |
| Stack vs heap | Stack: per-thread, fixed size (default 512KB–1MB). Heap: shared, GC-managed. Stack overflow = recursion too deep | `Thread.ofVirtual()` uses tiny stacks |
| Syscall cost | Crossing user→kernel boundary = ~100ns. Batching reads/writes reduces syscall overhead | `sendfile()` zero-copy for file transfer |
| Zero-copy | `sendfile()` moves data from file FD to socket FD entirely in kernel — no user-space buffer | Settlement file download, S3 streaming |
| Virtual threads (JDK 21+) | JVM threads mapped M:N onto carrier (OS) threads. Blocking syscall parks virtual thread, carrier thread freed | Java 21+ `Executors.newVirtualThreadPerTaskExecutor()` |

### 31.5 NUMA, CPU Affinity, and Interrupt Coalescing

| Concept | What it means | Where you see it |
|---|---|---|
| NUMA | Multi-socket servers: each CPU has local RAM. Accessing remote RAM = 2–4× slower | ECS task placement, JVM `-XX:+UseNUMA` |
| CPU affinity | Pin a thread to a specific CPU core — avoids cache invalidation from migration | Netty I/O thread pinning on high-throughput servers |
| Interrupt coalescing | NIC batches interrupts instead of one per packet — reduces context switches at high packet rates | `ethtool -C` on Linux; relevant for 10Gbps+ links |
| CPU cache hierarchy | L1 (4ns) → L2 (12ns) → L3 (40ns) → RAM (100ns). Cache-friendly data structures matter at high TPS | False sharing in concurrent `long[]` counters |

### 31.6 Where Each Concept Appears in This Project

| Ticket | Concepts learned |
|---|---|
| #17 — Netty TCP server | TCP 4-tuple, accept queue, SO_REUSEPORT, epoll, reactor pattern, C10K |
| #34 — Resilience4j bulkhead | Semaphore vs thread-pool bulkhead mirrors kernel counting semaphores |
| #68 — ZGC + latency tuning | Virtual memory, page faults, GC pause impact, HikariCP pool sizing, CPU affinity |
| #15 — terminal-simulator TCP client | Ephemeral ports, TIME_WAIT, connection reuse |
| #59 — KEDA + K8s HPA | L4 vs L7 load balancing, kernel round-robin vs ALB routing |
| #63 — k6 load test | File descriptor limits, accept queue overflow, context switch cost under load |

### 31.7 Resources

| Resource | Type | Notes |
|---|---|---|
| *Systems Performance* — Brendan Gregg | 📖💰 | The bible for kernel/OS performance; covers epoll, NUMA, CPU affinity |
| *Computer Networks* — Tanenbaum | 📖💰 | TCP state machine, socket internals, congestion control |
| [Linux man pages — epoll(7)](https://man7.org/linux/man-pages/man7/epoll.7.html) | 🆓 | Primary source for epoll semantics |
| [Beej's Guide to Network Programming](https://beej.us/guide/bgnet/) | 🆓 ⭐ | Best free intro to sockets; covers accept queue, SO_REUSEPORT |
| [The C10K Problem — Dan Kegel](http://www.kegel.com/c10k.html) | 🆓 | Original essay; still the clearest explanation of why threads don't scale |
| [Netty in Action](https://www.manning.com/books/netty-in-action) | 📖💰 | Reactor pattern, event loop, channel pipeline — directly applicable to #17 |
| *Linux Kernel Development* — Robert Love | 📖💰 | CFS scheduler, virtual memory, syscalls — for going deeper |

---

*This document is the single source of truth for prep. Concepts checklist (Part 1) is the reference while building. Phased plan (Part 2) is the execution schedule. Resources (Part 3) tells you where to go for each topic.*

*Last updated: May 2026 — based on Payswiff payments platform v1.0 (June target) and v2.0 (ongoing enhancements). Sections 29–30 added: NFC/Tap to Pay on iPhone and ISO 20022. Section 31 added: Systems Programming — networking, OS, kernel internals.*
