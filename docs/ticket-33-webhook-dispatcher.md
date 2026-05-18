# Ticket #33 — Webhook Dispatcher

Closes #33.

## What

Reliable async webhook delivery service: Kafka consumer on transaction events → HMAC-SHA256 signed HTTP POST → exponential backoff retry → Kafka DLQ on exhaustion → `webhook_deliveries` table tracking every attempt.

## Why

Merchants need real-time notification of payment outcomes to update their order systems. Synchronous delivery during the auth flow would add latency to the critical path; async Kafka-driven delivery decouples the merchant's reliability from the payment's reliability.

## Design Decisions

- **`HttpSender` and `DlqPublisher` as `@FunctionalInterface`** — keeps `WebhookDeliveryService` fully unit-testable with Mockito without a Spring context or real HTTP server. The real `RestClient` and `KafkaTemplate` implementations are wired in `WebhookConfig`.
- **Manual retry loop over `@Retryable`** — explicit loop makes attempt count tracking, per-attempt DB writes, and backoff calculation transparent. `@Retryable` would hide this and complicate the `webhook_deliveries` audit trail.
- **Exponential backoff: 1s → 2s → 4s → 8s → 16s** — `BASE_BACKOFF_MS * (1 << (attempt - 1))`. Production addition would be ±10% random jitter to avoid thundering herd on mass-failure.
- **`MANUAL_IMMEDIATE` Kafka ack mode** — offset committed synchronously only after `dispatch()` completes (delivered or DLQ'd). Auto-commit would silently lose events on crash mid-retry.
- **`RestClient` (Spring Boot 4)** — catches all exceptions and maps to 503 so the retry loop always sees an integer status code, never an uncaught exception.
- **HMAC-SHA256 signature**: `X-NexSwitch-Signature: sha256=<hex>` — identical to Stripe/Razorpay pattern; merchants verify with their stored secret.
- **`X-Idempotency-Key`** — UUID generated once per delivery, stable across retries so merchants can deduplicate.

## Test Coverage

| Test | Scenario |
|---|---|
| `givenMerchantWithWebhook_whenDeliverySucceeds_thenStatusDelivered` | 200 on first attempt |
| `givenTransientFailure_whenRetrySucceeds_thenStatusDelivered` | 500 × 2 then 200, attempt count = 3 |
| `givenAllAttemptsExhausted_thenStatusFailedAndDlqPublished` | 5 × 500, FAILED + DLQ published |
| `givenMerchantWithNoWebhookUrl_thenNoHttpCallMade` | No webhook configured — skip |
| `givenMerchantNotFound_thenNoHttpCallMade` | Unknown merchantId — skip |
| `givenSuccessfulDelivery_thenIdempotencyKeyHeaderIncluded` | Headers: X-Idempotency-Key + X-NexSwitch-Signature |
| `HmacSignerTest` × 4 | Correctness, determinism, secret sensitivity, payload sensitivity |

10 tests total, 0 failures.

## How to Verify

1. `docker compose up -d`
2. Start `webhook-dispatcher` and `merchant-simulator` (port 9000)
3. Run `terminal-simulator` — triggers `transaction.authorized` event on Kafka
4. `merchant-simulator` logs show `POST /webhook` with `X-NexSwitch-Signature` header
5. `webhook_deliveries` table shows `status = DELIVERED`, `attempt_count = 1`
6. Stop `merchant-simulator`, re-run — after 5 attempts `status = FAILED`, event in `webhook.dlq` topic
