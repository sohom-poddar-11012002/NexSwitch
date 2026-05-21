# Ticket #39 — Static QR + UPI Collect

## What

- **Static QR generation**: `GET /qr/static/{merchantId}` returns a permanent QR image whose UPI string encodes only VPA, merchant name, and MCC — no amount, no txnRef, no Redis TTL
- **UPI Collect (pull payment)**: `POST /upi/collect` initiates a debit request from the merchant to the payer VPA; persisted as PENDING with 3-minute default expiry
- **UPI Collect outcome webhook**: `POST /upi/collect/outcome` receives async APPROVED/REJECTED callback from Mock NPCI and updates collect request status

## Why

UPI supports two flows: push (QR/deep-link, customer initiates) and pull (Collect, merchant initiates). Static QR is a simpler permanent QR for walk-in merchants — no per-transaction session needed. Collect is the pull equivalent required for recurring and subscription-style flows.

## Design Decisions

- **Static QR is stateless** — no Redis, no DB row; the QR string is derived from merchant profile on every request. Reconciliation limitation is documented: two payments of the same amount from the same payer are indistinguishable without a txnRef.
- **`CollectRequest.Builder`** — `CollectRequest` is mutable-via-copy (not a record) because status transitions (`withStatus()`, `withNpciTxnId()`) are needed; sealed-result types (`InitiateCollectResult`) remain records.
- **`MockNpciPspAdapter`** — stub logging adapter; a real adapter would POST to Mock NPCI's REST endpoint. Domain sees only `UpiPspNotifier` interface.
- **Fallback VPA** — if `merchant.vpa` is null/blank, the static QR uses `{merchantId}@payswiff` as the payee address.
- **Bean Validation on all new request bodies** — consistent with #137 pattern; `UpiCollectController` validates payerVpa format (UPI VPA regex), amount (decimal 2dp), status (APPROVED|REJECTED enum pattern).

## Test Coverage

### Domain unit tests (`domain` module)
| Class | Tests |
|---|---|
| `GenerateStaticQRServiceTest` | 5 — unknown merchant → Failed, suspended → Failed, active → Generated, UPI string format, fallback VPA |
| `InitiateCollectServiceTest` | 6 — unknown merchant → Failed, suspended → Failed, valid → Initiated, saves+notifies PSP, PENDING+180s expiry, unique collectIds |

### Controller unit tests (`acquiring-service`)
| Class | Tests |
|---|---|
| `UpiCollectControllerTest` | 5 — valid initiate → 200+collectId, unknown merchant → 400, missing payerVpa → 400+violation, outcome APPROVED, outcome not found → 400 |

### QA scenarios (`qa-orchestrator`)
| Scenario | What it tests |
|---|---|
| `static-qr-generate` | GET /qr/static, asserts 200, upiString contains pa=/mc=, no am= |
| `upi-collect-initiate` | POST /upi/collect, asserts 200, collectId starts with COL |
| `upi-collect-outcome-approved` | full lifecycle: initiate → inject collectId → outcome APPROVED |

## How to Verify

```bash
# Run domain tests
mvn -pl domain test -Dgroups=unit

# Run acquiring-service tests
mvn -pl services/acquiring-service test -Dgroups=unit

# Trigger QA run (qa-orchestrator running)
curl -X POST http://localhost:8082/runs/trigger \
  -H 'Content-Type: application/json' \
  -d '{"runId":"upi-collect-run"}'
```
