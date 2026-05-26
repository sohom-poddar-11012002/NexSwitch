# G6 — Infrastructure Wiring

## What
Wired all 11 previously-stubbed infrastructure gaps in one PR so the application context can
start fully without missing beans or absent Kafka topics.

## Why
G5 observability was green; the remaining gaps were all in Application/Adapters plumbing — no
new domain logic required, so a single squash PR reduces merge churn.

## Design decisions
- `KafkaConfig` declares all 10 `NewTopic` beans (partitions=3, RF=1) — `KafkaAdmin` creates
  missing topics idempotently at startup; RF=1 is dev-only, prod uses RF=3.
- `RedisConfig` publishes `RedisTemplate<String,String>` with `StringRedisSerializer` on all
  four key/value/hashKey/hashValue axes — avoids Java serialization and keeps Redis CLI readable.
- `AuditPort` added to domain as a pure interface (zero Spring dependency); `JpaAuditAdapter`
  writes to the existing `audit_log` table from V3 migration (append-only; UPDATE/DELETE
  revoked from `nexswitch_app` at DB level).
- `LoggingNotificationAdapter` is a deliberate stub that logs instead of sending email — the
  Thymeleaf+SMTP adapter is a later sprint; the port contract is already in domain.
- `KafkaWebhookDispatchAdapter` publishes to the same topic the `webhook-dispatcher` service
  already consumes — HMAC signing is adapter-level inside webhook-dispatcher.
- `MockRefundAdapter` uses `@ConditionalOnProperty(upstream.provider=wiremock)` so the
  real ISO 8583 reversal adapter can be dropped in for staging with no code change.
- `ReconciliationService` + `ProcessRefundService` are plain Java domain services wired by
  `AdapterConfig`; domain module stays annotation-free.
- `MerchantConfigPublisher` closes the N49 gap — the listener already existed but nothing
  published; this component wraps `KafkaTemplate` and is injected wherever merchant config changes.
- Reconciliation-service gets `@EnableScheduling` + `ScheduledReconciliationTrigger` (`0 0 2 * * *`);
  Settlement-service gets Spring Batch `settlementJob` + `SettlementController` (POST + nightly cron).
- 5 stub services (chargeback, notification, reconciliation, settlement, merchant-simulator) each
  got at least one controller or consumer to make them functional Spring Boot apps.

## Test coverage
- `ProcessRefundServiceTest` — 4 tests: not-found, wrong status, success + state transition, over-refund guard
- `ReconciliationServiceTest` — 3 tests: matched, mismatch, empty
- All existing 628 tests (546 domain + 82 adapters) still pass

## How to verify
```bash
mvn test -pl domain,application,adapters
# settlement batch
curl -X POST http://localhost:8300/settlement/run
# reconciliation on demand
curl -X POST "http://localhost:8200/reconciliation/run?date=2026-05-25&networks=VISA,RUPAY"
# simulate merchant config change (triggers cache invalidation)
curl -X POST http://localhost:9001/simulate/merchant-config-change/MERCH0000001
```
