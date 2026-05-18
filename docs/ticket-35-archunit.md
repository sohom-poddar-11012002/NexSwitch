# Ticket #35 — ArchUnit Enforcement

## What
Added static bytecode architecture tests (ArchUnit) that enforce hexagonal architecture rules at build time. Violations now fail CI exactly like broken unit tests — no manual review needed.

## Why
With the QA orchestrator added as a second bounded context, the rule surface doubled. ArchUnit makes illegal imports (Spring in domain, adapters bypassing ports, field injection) a compile-time failure rather than a code-review catch.

## Design Decisions

| Decision | Rationale |
|---|---|
| Rules in both `domain/` and `qa-orchestrator/` | Each module is independently analyzable; domain module scans `com.nexswitch` broadly to also catch QA violations at the platform level |
| `@AnalyzeClasses(packages = "com.nexswitch.qa")` in QaArchitectureTest | Scoped to QA bounded context only — faster, clearer failure messages |
| `AssertionEvaluator` moved from `domain.service` → `application` | ArchUnit immediately caught that SpEL/MapAccessor imports violated the "QA domain must be Spring-free" rule — the fix is the architecturally correct `ExpressionEvaluator` outbound port pattern, mirroring how the production domain stays pure |
| `QaOrchestratorConfig` exempt from Rule 7 | Config classes exist to wire beans — they must reference adapters by design |

## Rules Added

### `DomainArchitectureTest` (4 new rules, Rules 7–10)
- **Rule 7**: QA domain must not import production domain classes
- **Rule 8**: QA domain services must be Spring-free (pure Java like production domain)
- **Rule 9**: Application services must not call repositories/adapters directly
- **Rule 10**: No `@Value` field injection anywhere in codebase

### `QaArchitectureTest` (9 rules, new test class)
1. QA domain has no Spring/JPA/Kafka/jPOS dependencies
2. QA domain does not import production domain
3. No `@Autowired` field injection
4. No `@Value` field injection
5. Channel adapters implement `TestChannelPort` and live in `adapter.channel`
6. REST controllers call use case ports, never domain service implementations
7. Application layer (except Config) does not depend on adapter implementations
8. Outbound ports (`*Port`, `*Repository`) are interfaces
9. Inbound use case ports (`*UseCase`) are interfaces

## Test Coverage
- 9 arch rules in `QaArchitectureTest` — all green
- 4 new arch rules in `DomainArchitectureTest` — all green (504 total in domain module)
- `AssertionEvaluatorTest` moved to `application` package; all 8 tests green
- Total qa-orchestrator: 33 tests, 0 failures

## Genuine Violation Found and Fixed
ArchUnit found a real violation during implementation: `AssertionEvaluator` was in `com.nexswitch.qa.domain.service` but imported `org.springframework.expression.*`. Fix:
1. Created `ExpressionEvaluator` outbound port (pure Java interface in `domain.port.outbound`)
2. Moved `AssertionEvaluator` to `com.nexswitch.qa.application`, now implements the port
3. `ScenarioExecutionEngine` injects `ExpressionEvaluator` (interface) — domain stays pure

## How to Verify
```bash
# Both must be BUILD SUCCESS with 0 failures
mvn test -pl domain
mvn test -pl services/qa-orchestrator
```
