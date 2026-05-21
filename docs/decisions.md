# Development Diary — NexSwitch

Running record of decisions made, problems hit, and fixes applied as the project was
built. Written for interview use — each entry has enough context to reconstruct the full
reasoning on the spot. References actual PRs, issues, files, and functions throughout.

---

## 1. Project Foundation

### Choosing the Architecture Pattern Before Writing a Line of Code

**Situation**
The project goal was to replicate how an Indian acquirer processor (Payswiff, Juspay,
Pine Labs) actually operates — ISO 8583 over TCP, HSM-backed PIN block handling, Kafka
event bus, three-way settlement reconciliation. The architecture had to demonstrate
production-grade thinking, not tutorial thinking.

**Planning — What I Considered**
Three options:

1. **Standard layered architecture** (controller → service → repository) — fastest to
   scaffold, familiar to every Java developer, but business logic ends up entangled with
   Spring annotations. Swapping a mock HSM for a real PKCS#11 one means touching service
   classes.

2. **Pure microservices, separate repos** — each service fully isolated, no shared code.
   The right answer at Uber scale. Wrong here because: (a) I'm one developer, (b) atomic
   refactoring across services becomes impossible without a versioned artifact pipeline,
   (c) the shared domain concepts (Money, AuthorizationCommand, TransactionStatus) would
   be duplicated in every service and drift.

3. **Hexagonal architecture (ports & adapters) in a monorepo** — domain defines
   interfaces (ports), infrastructure implements them (adapters). Single repo, multiple
   independently deployable Spring Boot services. Shared domain/adapters on the
   classpath.

**Decision**
I chose option 3. The core constraint: the `domain` module must have zero external
dependencies — no Spring, no JPA, no Kafka, nothing. Business rules must be testable
without infrastructure. All infrastructure lives in `adapters`.

**Tradeoff I accepted consciously**
Services share the `domain` module as a classpath dependency rather than a published
versioned artifact (Nexus/Artifactory). A breaking change to `AuthorizationCommand`
forces coordinated redeployment of every consumer. In production I'd publish `domain`
to a private Maven registry with semantic versioning. I didn't because the artifact
pipeline adds real overhead for zero demo return — and `acquiring-service` and
`payment-switch` are already protocol-coupled by ISO 8583 anyway.

**Result**
Module structure established in the initial scaffold. ArchUnit later (PR #127, issue
#35) made the hexagonal boundary physically unbreakable in CI.

---

### Stack Versions — The Upgrade That Broke Everything (PR #100)

**Situation**
Mid-project I decided to upgrade the full stack to the latest stable versions —
Java 26, Spring Boot 4, Testcontainers 2.x, PostgreSQL 18, Redis 8, Kafka KRaft. The
rationale: the portfolio needed to show current-year stack awareness, not Spring Boot 2
patterns from 2020.

**What broke**

- **Testcontainers 2.x changed the artifact prefix**: `org.testcontainers:postgresql`
  became `testcontainers-postgresql`, and the package structure changed to per-module
  imports (`org.testcontainers.postgresql`). Fixed in PRs #101, #102, #103.

- **KafkaContainer changed bootstrap format**: The `isRunning` assertion used a
  string format that changed with the `apache/kafka` image. Fixed in PR #103.

- **maven-compiler-plugin 3.13/3.14 don't support `--release 25`**: Had to pin to
  3.15.0. Plugin 4.x requires Maven 4. We're on Maven 3.9.15.

- **JaCoCo 0.8.13 has no Java 26 support**: `class file version 70` (Java 26 bytecode)
  caused JaCoCo to crash. Upgraded to 0.8.14 which added experimental Java 26 support,
  and added `<includes>com/payments/**</includes>` to the domain pom to prevent it from
  instrumenting generated code.

- **Spring Boot 4 removed `RestClient.Builder` auto-configuration**: Injecting it in
  `QaOrchestratorConfig` produced `NoSuchBeanDefinitionException` at startup. Fix:
  declare it manually as a `@Bean`.

- **Spring Boot 4 ships Jackson 3** (`tools.jackson.*`): `HarImporter` imported
  `com.fasterxml.jackson.databind.*` and failed at runtime. Updated all Jackson imports
  to `tools.jackson.*`.

**Lesson**
Do the stack upgrade in one dedicated PR with no feature work mixed in. Debugging
framework issues alongside business logic bugs costs twice the time. PRs #100–#103 were
all upgrade-only commits that should have been a single PR.

---

## 2. Domain Core

### Value Objects — Why Every Field Isn't Just a String (Issue #5, PR #105)

**Situation**
`AuthorizationCommand` needed to carry merchant ID, terminal ID, PAN hash, STAN, and
money. The obvious quick approach: make them all `String` or primitives.

**Problem with strings everywhere**
A method signature like `authorize(String merchantId, String terminalId, String pan,
String stan, double amount)` is a landmine. Every parameter is a `String` — you can
pass them in any order, the compiler won't catch it. And using `double` for money means
floating-point rounding in financial calculations.

**Decision**
Wrap every domain concept in a value object — `MerchantId`, `TerminalId`, `PanHash`,
`SystemTraceAuditNumber`. Each is a record with a factory method that validates on
construction:

```java
// domain/src/main/java/com/nexswitch/domain/model/vo/MerchantId.java
public record MerchantId(String value) {
    public static MerchantId of(String value) {
        if (value == null || !value.matches("MERCH\\d{7}"))
            throw new IllegalArgumentException("invalid merchant ID: " + value);
        return new MerchantId(value);
    }
}
```

`AuthorizationCommand` then takes `MerchantId merchantId`, not `String merchantId`.
You cannot pass a `TerminalId` where a `MerchantId` is expected — it's a compile error.

**Money specifically**

```java
// domain/src/main/java/com/nexswitch/domain/model/vo/Money.java
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("amount must be positive");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
}
```

`BigDecimal` only — `double` and `float` are banned by ArchUnit. The reason: `0.1 + 0.2`
is `0.30000000000000004` in IEEE 754. In settlement, that error compounds across
thousands of transactions and produces reconciliation mismatches that take days to
trace.

**Result**
9 value object types shipped in issue #5. `AuthorizationCommandTest` (PR #105) proves
illegal states can't be constructed. Zero `String` parameters in any domain method
signature.

---

### Sealed Interfaces for Results — Compiler-Enforced Exhaustive Handling (Issue #7)

**Situation**
`AuthorizationService.execute()` needed to return either an approval, a decline, or a
system error. Three options:

1. Return `String responseCode` and let the caller interpret it
2. Throw exceptions for declines and errors
3. Return a sealed type

**Decision**
Sealed interface with three permitted records:

```java
// domain/src/main/java/com/nexswitch/domain/model/AuthorizationResult.java
public sealed interface AuthorizationResult
    permits AuthorizationResult.Approved, AuthorizationResult.Declined, AuthorizationResult.Error {

    record Approved(String approvalCode, String responseCode) implements AuthorizationResult {}
    record Declined(String responseCode, String reason)       implements AuthorizationResult {}
    record Error(String message)                              implements AuthorizationResult {}
}
```

In `Iso8583RequestHandler`, the switch must be exhaustive:

```java
// adapters/src/main/java/com/nexswitch/adapters/inbound/iso8583/Iso8583RequestHandler.java
String responseCode = switch (result) {
    case AuthorizationResult.Approved a -> a.responseCode();
    case AuthorizationResult.Declined d -> d.responseCode();
    case AuthorizationResult.Error e    -> "96";
};
```

**Why not exceptions?**
Checked exceptions infect every method signature up the call chain. Unchecked exceptions
silently skip error handling. Sealed types force you to handle every case at every
callsite — the compiler rejects the switch if a `permits` variant is unhandled.

**Proven value**
When AMEX and DINERS were added to `PaymentNetwork` (PR #107), every switch over
`PaymentNetwork` became a compile error until those cases were handled. The type system
caught two missing cases in `Iso8583RequestHandler` before the tests ran.

---

### Transaction State Machine — 20 States, No Direct Field Updates (Issue #8)

**Situation**
A transaction moves through states: INITIATED → AUTHORIZATION_PENDING → AUTHORIZED →
SETTLEMENT_PENDING → SETTLED (happy path), with branches for DECLINED, REVERSED,
REVERSAL_PENDING, CHARGEBACK, and more. The obvious approach: set `status = AUTHORIZED`
directly in the service.

**Problem**
Direct field updates allow impossible transitions. Nothing stops code from setting
`SETTLED → INITIATED`. In a distributed system where the reversal monitor, the
authorization service, and the settlement batch all write to the same row, an illegal
transition is a data corruption bug, not an exception.

**Decision**
`TransactionStateMachine` holds an explicit valid-transition map:

```java
// domain/src/main/java/com/nexswitch/domain/service/TransactionStateMachine.java
private static final Map<TransactionStatus, Set<TransactionStatus>> VALID_TRANSITIONS = Map.of(
    INITIATED,               Set.of(AUTHORIZATION_PENDING),
    AUTHORIZATION_PENDING,   Set.of(AUTHORIZED, DECLINED, REVERSAL_PENDING),
    REVERSAL_PENDING,        Set.of(REVERSED),
    AUTHORIZED,              Set.of(SETTLEMENT_PENDING, REVERSAL_PENDING),
    // ...
);
```

`transition(current, next)` throws `InvalidStateTransitionException` if the transition
isn't in the map. All state writes go through this method — no direct status field
mutation anywhere in the codebase.

**Result**
This state machine guard is what made the reversal race condition safe (see section 4).
Without it, a late authorization response arriving after a reversal could silently flip
the state to AUTHORIZED.

---

## 3. Payment Processing Layer

### Netty for TCP — Not Spring Integration (Issue #16, PR #99)

**Situation**
The ISO 8583 terminal interface requires a raw TCP server on port 8583. Two realistic
options:

1. **Spring Integration TCP adapter** — native Spring, fits the container, familiar
2. **Netty** — non-blocking I/O, more setup, Netty is what production payment switches
   actually use

**Decision**
Netty. Spring Integration's TCP adapter uses one thread per connection. Under load
(hundreds of concurrent terminal sessions during a peak period), that's hundreds of
blocked threads sitting on network reads. Netty uses epoll (Linux) / kqueue (macOS) —
one thread handles thousands of connections via event notification. This is the reactor
pattern; it's how Netty solves the C10K problem.

**Implementation challenge**
Netty's `ChannelHandler` pipeline is stateless — it has no concept of Spring beans.
Getting a Spring-managed use case (`ProcessPaymentUseCase`) into a Netty handler
required injecting the handler itself as a `@Component` and wiring the pipeline in
`@PostConstruct`:

```java
// adapters/src/main/java/com/nexswitch/adapters/inbound/iso8583/Iso8583TcpServer.java
@PostConstruct
public void start() {
    ServerBootstrap b = new ServerBootstrap();
    b.group(bossGroup, workerGroup)
     .channel(NioServerSocketChannel.class)
     .childHandler(new ChannelInitializer<SocketChannel>() {
         @Override protected void initChannel(SocketChannel ch) {
             ch.pipeline()
               .addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2))
               .addLast(new LengthFieldPrepender(2))
               .addLast(iso8583RequestHandler); // Spring bean injected here
         }
     });
}
```

**Division of responsibility**
Netty owns the wire and framing. jPOS (`ISOMsg`) owns message parsing. The handler
delegates to the domain use case. No layer knows about the others' internals.

**Result**
6 tests, PR #99. The `LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2)` handles
the 2-byte length prefix that's standard for ISO 8583. Tested with the
`terminal-simulator` (issue #15, PR #98) sending real MTI 0100 messages over TCP.

---

### Wiring the Golden Path — 8-Step Authorization Chain (Issues #17–#18, PRs #105–#106)

**Situation**
The authorization flow needed to wire the full chain: terminal → acquiring-service →
payment-switch → mock-upstream → back. The steps:

1. Luhn check on BIN
2. Merchant and terminal lookup
3. Velocity / fraud check
4. Route to network (Visa/MC/RuPay/UPI by BIN range)
5. HSM PIN block verification
6. Forward to upstream
7. State machine transition: AUTHORIZATION_PENDING → AUTHORIZED / DECLINED
8. Kafka event publish

**Planning**
I wrote the `AuthorizationService` test first (TDD) — 12 test cases covering the golden
path and each failure branch before touching the implementation. The test mocks all
ports (HSM, network, repository) so it runs with zero infrastructure.

**Key decision — where Luhn lives**
Luhn validation is a domain rule — it's a mathematical property of the PAN itself, not
a database check or an external call. It belongs in the domain, not the adapter.
`LuhnValidator` is in `domain/src/main/java/com/nexswitch/domain/service/`.

**Key decision — PAN hashing at the boundary**
The PAN is hashed with SHA-256 in `Iso8583RequestHandler` — the first point of entry
into the system. Everything downstream sees only `PanHash`. This is the only design that
makes the PCI-DSS constraint enforceable: if the domain only ever sees a hash, there's
nothing to accidentally log.

**Result**
Golden path end-to-end: MTI 0100 from `terminal-simulator` → Netty → `AuthorizationService`
→ mock-upstream → Field 39 `00` (approved) back to terminal. Verified with Postman
collection and the terminal simulator CLI.

---

## 4. Resilience & Reliability

### Circuit Breaker Scoped to the HSM (Issue #34, PR #110)

**Situation**
External calls need circuit breakers — that's standard. The question was granularity:
one global circuit breaker, or one per dependency type?

**Decision**
One Resilience4j circuit breaker per external dependency type, scoped by name. The HSM
gets its own breaker (`hsm`), the upstream network gets its own (`upstream-network`),
the webhook dispatcher gets its own.

**Why not global**
An HSM failure has a specific blast radius: all PIN-bearing card-present transactions
fail; contactless and QR flows continue. A database slowdown has a different blast
radius: all transactions are slow. A global breaker conflates these — opening on DB
slowness would kill contactless flows unnecessarily. Scoped breakers open only on the
specific dependency that's failing.

**Implementation — wrapping HsmPort**
`ResilientHsmAdapter` wraps `HsmPort` with `@CircuitBreaker(name = "hsm")`. It's the
only place the circuit breaker annotation appears for HSM calls:

```java
// adapters/src/main/java/com/nexswitch/adapters/outbound/hsm/ResilientHsmAdapter.java
@Override
@CircuitBreaker(name = "hsm", fallbackMethod = "hsmFallback")
public String translatePinBlock(byte[] pinBlock, String ksn, String zpkAlias) {
    return delegate.translatePinBlock(pinBlock, ksn, zpkAlias);
}
```

The delegate is injected by qualifier (`@Qualifier("hsmDelegate")`) — allowing the
`mock` vs `softhsm` swap without touching `ResilientHsmAdapter`.

---

### Reversal Race Condition — The Hardest Concurrency Bug (Issue #32, PR #108)

**Situation**
The reversal flow: terminal sends 0100 (auth request) → acquiring-service forwards to
network → network goes silent → `TimeoutReversalMonitor` sends 0400 (reversal) → late
0110 (auth response) arrives.

If the late response is processed after the reversal commits, we have a transaction that
was reversed but then flipped to AUTHORIZED. Money has moved but the records say
reversed.

**Planning**
I mapped the race on paper before writing code:

```
Thread A (reversal):    read PENDING → transition to REVERSAL_PENDING → commit
Thread B (late 0110):  read PENDING → transition to AUTHORIZED → commit  ← WINS if B reads before A commits
```

The fix must make one of these transitions fail when they race. Options:

1. **Optimistic locking** — `@Version` field, retry on `OptimisticLockException`. Problem:
   the retry would try to authorize a transaction that's being reversed — wrong behavior.

2. **Pessimistic locking** — `SELECT FOR UPDATE` inside a transaction. Only one thread
   gets the lock; the other reads the committed state after the lock releases. Correct
   behavior but adds latency to every authorization.

3. **State machine guard** — `TransactionStateMachine.transition()` is called inside
   the database transaction. If Thread A commits `REVERSAL_PENDING` first, Thread B's
   state machine call sees `REVERSAL_PENDING` as current state and
   `AUTHORIZED` is not in its valid transitions → throws `InvalidStateTransitionException`
   → transaction rolls back → late 0110 is discarded.

**Decision**
Option 3 — the state machine guard, backed by a pessimistic lock (`SELECT FOR UPDATE`)
on the transaction row. The lock serializes the race; the state machine makes the
loser fail explicitly rather than silently succeed.

**Result**
PR #108. `TimeoutReversalMonitorTest` covers the race with 7 tests including a
concurrent execution test that runs both threads simultaneously and asserts only one
transition succeeds.

---

## 5. Security & PCI-DSS

### ArchUnit as a PCI-DSS Enforcement Tool (Issue #35, PR #127)

**Situation**
By issue #34, the codebase had grown to ~40 Java source files across 4 modules. Keeping
the hexagonal boundaries clean by convention was already fragile — one `@Autowired`
import in a domain class would silently break the architecture.

**Planning**
I defined 9 rules before writing the ArchUnit test:

1. Domain has no external dependencies (no Spring, no JPA, no Kafka)
2. Application layer has no business logic (no `if` on business conditions)
3. Application does not import adapter implementations (Rule 9 — the tricky one)
4. Adapters do not import application-layer classes
5. No field injection (`@Autowired` on fields) anywhere
6. No `double` or `float` in domain classes
7. All domain objects are records or sealed interfaces
8. All repository ports are in `domain.port.outbound`
9. No domain class directly imports a JPA annotation

**Rule 9 problem — conditional HSM wiring**
When I added `SoftHsm2HsmAdapter`, my first instinct was to put the
`@ConditionalOnProperty` bean in `AdapterConfig` (application module). That file would
need to import `SoftHsm2HsmAdapter` — an adapter class — violating Rule 9.

**Fix**
Moved conditional wiring into `HsmAdapterConfig` *inside the adapters module*:

```java
// adapters/src/main/java/com/nexswitch/adapters/outbound/hsm/HsmAdapterConfig.java
@Configuration
class HsmAdapterConfig {
    @Bean("hsmDelegate")
    @ConditionalOnProperty(name = "hsm.provider", havingValue = "softhsm")
    public HsmPort softHsmDelegate(SoftHsm2HsmAdapter softHsm) { return softHsm; }

    @Bean("hsmDelegate")
    @ConditionalOnProperty(name = "hsm.provider", havingValue = "mock", matchIfMissing = true)
    public HsmPort mockHsmDelegate(MockHsmAdapter mock) { return mock; }
}
```

Spring Boot's classpath scan finds it automatically — no explicit import needed in the
application module.

**Result**
PR #127. ArchUnit runs in the `test` phase. A CI violation produces a readable failure
message identifying the offending import. All 9 rules green. This became the safety net
for all subsequent development.

---

### DUKPT — Why Not Use jPOS's Implementation (Issue #36, PR #141)

**Situation**
PIN block decryption requires DUKPT (Derived Unique Key Per Transaction, ANSI X9.24-2004).
jPOS — already a dependency for ISO 8583 parsing — has `JCESecurityModule` which
includes DUKPT. I evaluated it seriously before deciding.

**Why I rejected jPOS SM**
`JCESecurityModule` is designed to run inside the jPOS Q2 container. Using it as a
standalone DUKPT library drags in Q2's lifecycle, configuration management, and XML
deploy descriptors. The DUKPT algorithm itself is ~100 lines of JCE calls. The overhead
wasn't justified.

**Planning the implementation**
I read the ANSI X9.24-2004 spec and verified the algorithm against three independent
sources (the spec itself, jPOS's open-source SM implementation, and open-payment-lib test
vectors) before writing a line of code.

Key insight from the spec: the 21-bit encryption counter occupies the rightmost 21 bits
of the 80-bit KSN. The counter-to-KSN-byte mapping is:

```
counter bit i → byte index (7 - i/8), bit position (i % 8)
```

I got this wrong in the first draft — I was mapping into the wrong byte. The IPEK test
passed (it doesn't depend on counter bits) but the transaction key test failed. Debugging
the bit mapping was the slowest part of this ticket.

**Test vector problem**
My initial expected transaction key value (`27F66D5244FF621EAA6F6120EDEB427F`) came
from a forum post using a *symmetric* BDK (`0123456789ABCDEF0123456789ABCDEF`) instead
of the ANSI spec BDK (`0123456789ABCDEFFEDCBA9876543210`). The IPEK test passed against
the spec vector (`6AC292FAA1315B4D858AB3A3D7D5933A`) but the transaction key expected
value was wrong.

Fix: computed the correct transaction key by running my verified IPEK through one
derivation step, cross-checked against jPOS source code. Updated expected values to
`3D63B02F4D50E611F330EC340B460D0D`.

Relevant test: `adapters/src/test/java/com/nexswitch/adapters/outbound/hsm/DukptKeyDerivationTest.java`

**Package visibility bug**
`DukptKeyDerivation` is in `com.nexswitch.adapters.outbound.hsm.pkcs11`.
`SoftHsm2HsmAdapter` is in `com.nexswitch.adapters.outbound.hsm`. Methods were
package-private by default — `SoftHsm2HsmAdapter` couldn't call them across the
package boundary.

Fix: made all methods and `BDK_VARIANT` `public static`. The class is `final` with a
private constructor so there's no encapsulation risk.

**Result**
PR #141 merged 2026-05-20. 9 DUKPT unit tests, all green. Full test suite: 557 tests
passing. `DukptKeyDerivation.java`, `SoftHsm2Session.java`, `SoftHsm2HsmAdapter.java`,
`HsmAdapterConfig.java` all new in this PR.

---

### SoftHSM2 via SunPKCS11 — Staging-Grade HSM Without Hardware (Issue #36, PR #141)

**Situation**
Real PKCS#11 HSMs (Thales Luna, Safenet) cost tens of thousands of dollars. For staging
and demo I needed a software HSM that speaks the actual PKCS#11 protocol — not a mock
that just returns hardcoded bytes.

**Decision**
SoftHSM2 via the JDK-bundled `SunPKCS11` provider. SoftHSM2 is PKCS#11-compliant and
used by OpenDNSSEC, Let's Encrypt, and payment test labs. The `SunPKCS11` provider
bridges Java's JCE API to any PKCS#11 library.

**Implementation detail — inline config string**
JDK 9+ requires an inline config string prefixed with `--\n`:

```java
// adapters/src/main/java/com/nexswitch/adapters/outbound/hsm/pkcs11/SoftHsm2Session.java
String config = String.format("--\nname=SoftHSM2-%d\nlibrary=%s\nslot=%d\n",
    slot, libraryPath, slot);
Provider base = Security.getProvider("SunPKCS11");
pkcs11Provider = base.configure(config);
Security.addProvider(pkcs11Provider);
```

One provider per slot. On `@PreDestroy`, the provider is removed from `Security` to
prevent "provider already registered" errors on dev-mode hot reload — this bit me once
during a failed startup where the Spring context was partially initialized and retried.

**Key extraction — the hardware HSM gap**
On SoftHSM2, keys are extractable: `keyStore.getKey(alias, pin).getEncoded()` returns
the raw key bytes. This is needed for DUKPT derivation (we need the BDK bits).

On real hardware HSMs, keys are non-extractable: `getEncoded()` returns `null`. Real
HSMs provide vendor-specific commands for DUKPT (Thales uses the `A6` command). I
documented this gap explicitly in
`docs/ticket-36-softhsm2-dukpt.md` and in `SoftHsm2HsmAdapter` — it's visible, not
hidden.

**Environment variable swap**
```yaml
# services/acquiring-service/src/main/resources/application.yml
hsm:
  provider: ${HSM_PROVIDER:mock}      # mock (CI/local) | softhsm (staging)
  pkcs11:
    library: ${HSM_LIBRARY:/usr/lib/softhsm/libsofthsm2.so}
    slot: ${HSM_SLOT:0}
    pin: ${HSM_PIN:1234}
    bdk-alias: payments-bdk
    zpk-alias: payments-zpk
    mak-alias: payments-mak
```

No code changes needed to switch between mock and real PKCS#11 at runtime.

---

### Two-Step PIN Translation — PIN Never Plaintext Outside HSM (Issue #36)

**Situation**
PCI-DSS requirement: a PIN block must never exist in plaintext outside an HSM security
boundary. The terminal sends a DUKPT-encrypted PIN block (ISO 8583 Field 52) and a KSN
(Field 53). The acquiring-service needs to re-encrypt it under a ZPK (Zone PIN Key) for
onward transmission to Visa/Mastercard.

**Design**
Two-step operation inside one `HsmPort.translatePinBlock()` call:

1. `dukptDecrypt()` — derive IPEK from BDK + KSN, derive transaction key, apply PIN
   variant, decrypt the PIN block → store plaintext in `ConcurrentHashMap<UUID, byte[]>`
   keyed by a UUID handle
2. `reencryptUnderZpk()` — retrieve from map, encrypt under ZPK, zero the plaintext
   array with `Arrays.fill(plainBlock, (byte)0)`, remove from map

The plaintext PIN block lives only inside the adapter as a local `byte[]` for the
duration of the operation. The service layer holds only the UUID handle — which maps to
nothing after `reencryptUnderZpk()` completes.

```java
// domain/src/main/java/com/nexswitch/domain/service/AuthorizationService.java
if (cmd.pinBlock() != null && cmd.ksn() != null) {
    hsmPort.translatePinBlock(cmd.pinBlock(), bytesToHex(cmd.ksn()), "zpk");
}
```

**`AuthorizationCommand` change**
Added `byte[] ksn` as field 12 (ISO 8583 Field 53). This required updating:
- `AuthorizationCommand.java` — new record component
- `Iso8583RequestHandler.java` — extract `req.getBytes(53)` as 12th arg
- `AuthorizationServiceTest.java` — `null` as 12th arg in `validCommand()`
- `AuthorizationCommandTest.java` — `null` in all 5 test constructors

---

## 6. QA Platform

### Why a Separate QA Orchestrator Service (Issues #111–#128, PRs #121–#129, #136)

**Situation**
Manual testing of ISO 8583 flows is painful — you need a terminal simulator, a running
Kafka broker, a Postgres instance, and the timing has to be right for reversal tests.
I wanted automated scenario execution with real assertions.

**Decision**
A standalone `qa-orchestrator` Spring Boot service with its own domain model, separate
from the payments domain. The orchestrator:
- Loads YAML-defined test scenarios from classpath
- Executes steps (ISO 8583, REST, Kafka assertions, wait-for-human)
- Publishes results via SSE to the QA portal frontend
- Supports adversarial scenarios (network chaos, clock skew, duplicate messages)

The QA domain is fully hexagonal too — `TestScenario`, `TestStep` (sealed), `StepResult`
(sealed) are all records. The same patterns from the payments domain apply.

**Sealed TestStep — the right call**

```java
// services/qa-orchestrator/src/main/java/com/nexswitch/qa/domain/model/TestStep.java
public sealed interface TestStep
    permits TestStep.Iso8583Step, TestStep.RestStep, TestStep.KafkaAssertStep,
            TestStep.WaitForHuman, TestStep.ChaosStep { ... }
```

`ScenarioExecutionEngine` dispatches on step type with an exhaustive switch. Adding a
new step type is a compile error until handled — the same safety as `AuthorizationResult`.

**WaitForHuman virtual thread**
Human-in-the-loop steps park a virtual thread (`Thread.ofVirtual()`) until unblocked
by a REST call from the portal. Virtual threads make this cheap — parking 100 concurrent
human-step threads costs ~1MB vs ~100MB with platform threads.

**Result**
4 phases, 4 PRs (#121, #128, #129, #136). 28 test scenarios including 11 adversarial.
QA portal: Next.js 15 App Router, SSE live stream, scenario browser, suite scheduler,
HAR importer for replay.

---

### The `.next/` Commit Mistake (PR #136 Followup)

**What happened**
When committing the QA portal frontend changes, `frontend/qa-portal/` had no
`.gitignore`. The first commit included 170+ `.next/` build output files — minified JS
bundles, webpack chunks, source maps. The committed blob was ~4MB of generated files.

**Fix sequence**
```bash
git reset HEAD~1                                    # uncommit, keep working tree
echo ".next/" >> frontend/qa-portal/.gitignore     # create gitignore
git rm -r --cached frontend/qa-portal/.next/       # untrack from index
git add frontend/qa-portal/.gitignore              # stage gitignore
git commit -m "fix(#125): ..."                     # recommit without .next/
```

**Root cause**
I scaffolded the Next.js app with `create-next-app` but didn't create `.gitignore`
before the first `git add`. The rule now: `.gitignore` is the first file created in any
new frontend or build-output directory, before any other file is staged.

Relevant file: `frontend/qa-portal/.gitignore`

---

## 7. CI / CD & Operational

### GitHub Actions — Single Pipeline, Per-Module JaCoCo (Cross-Cutting)

**Situation**
The monorepo has 4 Java modules (domain, application, adapters, services/*) and 3
Next.js frontends. CI needed to: compile all modules, run all tests, enforce JaCoCo
coverage on the domain, and fail on ArchUnit violations.

**Decision**
Single GitHub Actions workflow, Maven multi-module build (`mvn verify`). JaCoCo
coverage enforcement only on the `domain` module — not adapters, not services. Reason:
domain code is pure business logic, every branch should be tested. Adapter code
(PKCS#11 wiring, Kafka consumer deserialization) requires real infrastructure to test
meaningfully and belongs in integration tests, not unit test coverage metrics.

**JaCoCo Java 26 problem**
JaCoCo 0.8.13 crashed on Java 26 bytecode (class file version 70): `Unsupported class
file major version 70`. Fixed by upgrading to 0.8.14 (experimental Java 26 support) and
adding `<includes>com/payments/**</includes>` to prevent JaCoCo from instrumenting
generated sources.

**Explicit timeout enforcement**
Every external call has a timeout declared in code (not left to OS defaults):

| Dependency      | Timeout | Why                                                      |
|-----------------|---------|----------------------------------------------------------|
| HSM             | 500ms   | Slow HSM = hardware fault; retrying won't help           |
| Payment network | 15s     | Matches terminal ISO 8583 timeout; after this, reversal  |
| Webhook         | 5s      | Merchant SLA; failure triggers retry queue               |
| Database        | 3s      | Slow DB = systemic issue; fail fast, circuit breaker     |

The HSM timeout at 500ms is the strictest — deliberately. A PIN block decrypt that
takes more than half a second is not a transient glitch.

---

### Docker Compose — Local Dev Stack (Cross-Cutting)

**Situation**
Local development requires Postgres, Kafka (KRaft mode, no Zookeeper), Redis, and
SoftHSM2. Running these manually before every dev session is friction that causes people
to skip tests.

**Decision**
`docker-compose.yml` at the project root brings up the full stack with `docker compose up -d`.
Testcontainers (`test-support` module) starts fresh containers for CI — no shared state
between CI runs, no port conflicts.

The `docker-compose.yml` uses named volumes for Postgres data so the dev database
survives container restarts. Kafka uses the `apache/kafka` KRaft image (no Zookeeper)
which simplified the compose file by two services.

**Testcontainers singletons**
`IntegrationTestBase` starts Postgres and Kafka as singleton Testcontainers instances
shared across all integration test classes in a JVM. Without singletons, each test class
starts and stops its own container — a 90-second test suite becomes a 15-minute one.

```java
// test-support/src/main/java/com/payments/test/IntegrationTestBase.java
@SpringBootTest
public abstract class IntegrationTestBase {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
        .withReuse(true);
    static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("apache/kafka").asCompatibleSubstituteFor("confluentinc/cp-kafka"))
        .withReuse(true);
    // ...
}
```

The `.withReuse(true)` flag keeps the container running between test runs in local dev
— a cold start on CI, a warm reuse on dev. Saves ~30s per run locally.
