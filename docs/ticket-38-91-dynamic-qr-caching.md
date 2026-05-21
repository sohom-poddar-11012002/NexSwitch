# Ticket #38 + #91 — Dynamic QR (Redis TTL) + Caching Strategies

## What

**#38 — Dynamic QR payment generation and lifecycle:**
- `POST /qr/generate` — creates a Redis-backed QR session, generates ZXing QR image (Base64 PNG), returns txnRef + expiresAt
- `GET /qr/status/{txnRef}` — polls Redis session status (PENDING → COMPLETED / EXPIRED)
- `POST /upi/credit` — NPCI credit notification; validates amount, transitions session to COMPLETED
- QR session TTL is derived from the domain's `expiresAt` (5 min, configurable via `qr.session.ttl-minutes`)

**#91 — Caching strategies (L1 + L2, stampede prevention, event-driven invalidation):**
- `CachingMerchantRepository` — L1 Caffeine (30s, 1,000 entries) + L2 Redis (`merchant:config:{merchantId}`, 5min ±10% jitter) with SETNX stampede lock
- `MerchantCacheInvalidationListener` — Kafka consumer on `merchant.config.updated` → proactive L1+L2 eviction; TTL is a safety net only
- `CachingBinLookupAdapter` (pre-existing) + `RedisIdempotencyAdapter` (pre-existing) complete the caching strategy table

## Why

Dynamic QR is the primary payment channel for UPI P2M at POS terminals. The 5-minute TTL is per NPCI specification for dynamic QR codes. Redis provides distributed session state so multiple acquiring-service replicas all see the same session — stateless horizontal scaling.

Caching merchant profiles eliminates repeated Postgres roundtrips on the hot authorization path. Every ISO 8583 authorization requires a merchant profile lookup; at 1,000 TPS each DB hit costs 3ms = 3 seconds of latency per second at load.

## Design decisions

1. **TTL from domain expiresAt, not hardcoded in adapter** — `QRSession.expiresAt` is the authoritative expiry; `RedisQrSessionAdapter.save()` computes `Duration.between(now, expiresAt)` to set the Redis TTL. The domain concept doesn't bleed Redis semantics.

2. **Flat DTO for Redis serialization** — `MerchantProfile` uses value objects (`MerchantId`, `Money`) that Jackson can't deserialize by default. A flat `MerchantDto` record with primitive fields is serialized instead; `findById()` reconstructs the domain object on deserialize. Same approach as `SessionDto` in `RedisQrSessionAdapter`.

3. **ZXing Error Correction Level M** — 15% data restoration tolerance, standard for payment QRs where POS screens may be dirty or partially obscured.

4. **UPI credit idempotency via session status check** — if status is already COMPLETED, `POST /upi/credit` returns `ALREADY_PROCESSED` (not an error). NPCI can retry safely.

5. **`vpa` added to MerchantProfile** — needed by `QRSessionManager.buildUpiString()`. Already in `merchants` table (V2 migration) and `MerchantEntity`; added to the domain record and mapper this ticket.

## Test coverage

| Class | Tests | Approach |
|---|---|---|
| `QRSessionManager` | 13 (pre-existing) | Unit |
| `GenerateQRService` | 6 | Unit (Mockito) |
| `RedisQrSessionAdapter` | 6 | Unit (Mockito StringRedisTemplate) |
| `ZxingQrImageGeneratorAdapter` | 3 | Unit (actual ZXing, PNG magic bytes) |
| `CachingMerchantRepository` | 4 | Unit (Mockito) |
| QA scenarios | 3 | REST channel via qa-orchestrator |

## QA scenarios

| Scenario | Run |
|---|---|
| `dynamic-qr-generate-pending` | `qr-run` |
| `dynamic-qr-payment-completed` | `qr-run` |
| `qr-session-expired` | `qr-run` |

## How to verify

```bash
# Start infrastructure
docker compose up -d postgres redis kafka mock-upstream acquiring-service

# Generate QR
curl -X POST http://localhost:8080/qr/generate \
  -H 'Content-Type: application/json' \
  -d '{"merchantId":"MERCH0000999","terminalId":"TERM0001","amount":"6000.00","currency":"INR","orderId":"order-001"}'

# Poll status (replace TXN_... with actual txnRef)
curl http://localhost:8080/qr/status/TXN_...

# Simulate NPCI credit
curl -X POST http://localhost:8080/upi/credit \
  -H 'Content-Type: application/json' \
  -d '{"npciTxnId":"NPCI12345","payerVpa":"customer@upi","payeeVpa":"merch0000999@payswiff","amount":"6000.00","txnRef":"TXN_..."}'

# Check RedisInsight at http://localhost:5540 — qr:session:TXN_... key with 5 min TTL
```
