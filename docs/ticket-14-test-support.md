# Ticket #14 — Test Support Module

Closes #14.

## What

Built the shared `test-support` module: Testcontainers singletons for Postgres/Redis/Kafka, four fixture classes covering every transaction/ISO 8583 variant needed by integration tests, and a `ContainerStartupIT` smoke suite.

## Why

All service integration tests depend on real infrastructure (Postgres, Redis, Kafka). Without singletons, each test class would spin up its own container — a 9-service repo would start containers 9× per CI run, blowing the 10-minute budget. One JVM, one set of containers, shared across all suites.

## Design Decisions

- **`withReuse(true)` singleton pattern** — Testcontainers 2.x keeps containers alive across test class boundaries within the same JVM. `@DynamicPropertySource` in `IntegrationTestBase` wires all Spring datasource/broker properties once at startup.
- **Fixture state paths must respect `TransactionStatus` transition rules** — `authorized()` must go `INITIATED → AUTHORIZATION_PENDING → AUTHORIZED`, not directly `INITIATED → AUTHORIZED`. Using `withStatus()` shortcut would bypass the state machine and produce fixtures that can't be used to test valid transitions. Fixed during PR review.
- **`test-support` is `<scope>test</scope>` in all service POMs** — the module compiles production fixture code but is never on a production classpath. Enforced via Maven scope, not convention.
- **`BinInfoFixture` included** — BIN data needed by routing and fraud tests; centralised here so service tests don't duplicate BIN construction.
- **Failsafe plugin activated in `test-support/pom.xml`** — `mvn verify` runs integration tests with `DOCKER_HOST` passed through to the forked JVM so Docker-in-Docker works in CI.

## Test Coverage

`ContainerStartupIT` — 7 smoke tests:
- Postgres, Redis, Kafka containers all start and are reachable
- `TransactionFixture.initiated()`, `authorized()`, `reversalPending()`, `chargebackReceived()` build without throwing
- `Iso8583MessageFixture.normalPurchase0100()` produces a parseable `ISOMsg`

## How to Verify

```
mvn verify -pl test-support
# 7/7 integration tests pass
# Second run completes in ~6s (containers reused) vs ~177s first run
```
