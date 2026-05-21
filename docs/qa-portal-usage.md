# QA Portal — Usage Guide

The QA Portal is the browser-based interface for running, monitoring, and reviewing
automated tests against the NexSwitch acquiring service. It connects to the
**qa-orchestrator** backend, which executes scenarios over a live ISO 8583 TCP connection
to the acquiring service.

> **Master test plan:** `docs/qa-test-plan.md` contains the full 179-TC catalog with
> priority, environment, and implementation status for every planned test. This document
> covers how to *use* the portal and lists which scenarios exist today. Add a row here
> whenever a new scenario YAML is committed.

### Test Coverage Overview (20 categories)

| # | Category | Scenarios | Status |
|---|---|---|---|
| 1 | Authorization — happy path | 6 | ✅ implemented |
| 2 | Reversal | 1 | ✅ implemented |
| 3 | Boundary / input rejection | 8 | ✅ implemented |
| 4 | Cryptography / HSM | 3 | ✅ implemented |
| 5 | Application security (MAC, replay, injection) | 5 | ✅ implemented |
| 6 | Network security (TLS, mTLS, cert) | 3 | ⬜ planned |
| 7 | Routing & resilience (BIN, network failover) | 3 | ⬜ planned |
| 8 | Race conditions / concurrency | 2 | ✅ implemented |
| 9 | Data consistency (DB assertions) | 4 | ⬜ planned |
| 10 | Chaos engineering | 4 | ✅ implemented |
| 11 | Performance baselines (latency SLA) | 3 | ⬜ planned — k6 (#144) |
| 12 | Observability (logs, traces, metrics) | 3 | ⬜ planned |
| 13 | Compliance (PCI DSS, RBI mandates) | 5 | ⬜ planned |
| 14 | Contract testing (Pact consumer/provider) | 2 | ⬜ planned |
| 15 | Analytics (reports, trends, pass rate) | 3 | ⬜ planned |
| 16 | Frontend E2E (Playwright, 3 portals) | 5 | ⬜ planned — #143 |
| 17 | Offline / FSE (terminal reconnect, queue) | 3 | ⬜ planned |
| 18 | Business domain flows (EMI, DCC, partial approval) | 4 | ⬜ planned |
| 19 | Fraud / AI / RAG pipeline | 4 | ⬜ planned |
| 20 | Localization (INR, IST, RBI limits) | 3 | ⬜ planned |

> **Performance / stress testing** (sustained TPS, p95 latency ramp-up) is handled by
> k6 and Gatling — **not** the qa-orchestrator. See ticket #144. The qa-orchestrator
> runs correctness and race condition scenarios only.

---

## Accessing the Portal

| Environment | URL |
|---|---|
| Local dev | `http://localhost:3000` |
| Staging | `http://qa.nexswitch.internal:3000` |

The portal requires the **qa-orchestrator** service to be running on port `8700`.
If the orchestrator is unreachable, pages will display a warning banner — no scenarios
can be triggered until it is restarted.

---

## Navigation

| Page | What it shows |
|---|---|
| **Scenarios** | Every loaded test scenario, grouped by platform → project → feature |
| **Runs** | Pre-configured run definitions and their trigger buttons |
| **Suites** | Multi-run suites (e.g., the full regression suite run before deployments) |
| **Reports** | Execution history with pass/fail breakdowns per scenario |
| **Recorder** | HAR import and proxy recorder for capturing new scenarios from real traffic |

---

## Running Tests

### Trigger a single run
1. Go to **Runs**.
2. Find the run you want (e.g., *Golden path — authorization happy path*).
3. Click **Trigger**. Variable overrides are optional (defaults come from the run YAML).
4. You are redirected to the live execution view. Steps update in real time via SSE.

### Trigger the full regression suite
1. Go to **Suites**.
2. Click **Trigger** on *v1 full regression suite*.
3. The suite runs all five runs in sequence. A single `FAIL_FAST` failure stops the suite.

### Run a single scenario in isolation
1. Go to **Scenarios** and click any scenario card.
2. The detail page shows the YAML definition and a **Trigger** button.
3. Use this during development to verify a single new scenario without running the whole suite.

---

## Reading Results

After a run or suite completes, the Reports page shows a timeline of every scenario
execution. Each row expands to show individual step results:

| Status | Meaning |
|---|---|
| `PASSED` | All `assert` steps evaluated to `true` |
| `FAILED` | At least one `assert` evaluated to `false`, or a step threw an exception |
| `CANCELLED` | A `fail_fast: true` step failed and aborted the remaining steps |

**Captured fields** — every `send` step captures the 0110 response into a named variable
(e.g., `auth_response`). Subsequent `assert` steps reference it:
```
auth_response['field39'] == '00'   # Response code approved
auth_response['approval_code']     # Authorization code (Field 38)
auth_response['field91_hex']       # ARPC bytes as uppercase hex (chip transactions only)
```

---

## Scenario Catalog

Scenarios are grouped below by feature area. Each row lists what the scenario sends,
what it asserts, and which environment it is meaningful in.

### Authorization — happy path approvals

| Scenario ID | Card network | What it sends | Asserts | Environment |
|---|---|---|---|---|
| `visa-auth-approved` | Visa | PAN `4539…6467`, chip+PIN (F22=051), ₹6,000 | field39=00 | All |
| `rupay-auth-approved` | RuPay | PAN `6521…`, chip+PIN, ₹500 | field39=00 | All |
| `mastercard-auth-approved` | Mastercard | PAN `5425…`, chip+PIN, ₹1,200 | field39=00 | All |
| `amex-auth-approved` | Amex | PAN `378282246310005` (15 digits), chip+PIN, ₹5,000 | field39=00 | All |
| `diners-auth-approved` | Diners Club | PAN `30569309025904` (14 digits), chip+PIN, ₹3,500 | field39=00 | All |
| `full-lifecycle` | Visa | Auth → wait-for-human receipt check → assert approval | field39=00 + manual check | All |

### Authorization — reversal

| Scenario ID | What it sends | Asserts | Environment |
|---|---|---|---|
| `reversal-after-auth` | Auth (0100) followed by reversal (0400) with same STAN | First field39=00; reversal field39=00 | All |

### Boundary — rejection cases

| Scenario ID | What it sends | Asserts | Environment |
|---|---|---|---|
| `invalid-luhn` | PAN that fails Luhn check | field39=14 (Invalid card) | All |
| `zero-amount` | Amount field = 0 | field39=13 (Invalid amount) | All |
| `null-pan` | No PAN field | field39=14 | All |
| `seventeen-digit-pan` | 17-digit PAN | field39=14 | All |
| `unknown-bin` | BIN with no routing rule | field39=57 (Transaction not permitted) | All |
| `wrong-currency` | Currency code not in ISO 4217 | field39=30 (Format error) | All |
| `max-amount` | Amount at upper boundary (₹9,99,999.99) | field39=00 or 61 | All |
| `expired-card` | Expiry date in the past (F14) | field39=54 (Expired card) | All |

### Security — HSM cryptography

| Scenario ID | What it sends | Asserts | Environment |
|---|---|---|---|
| `chip-pin-dukpt-approved` | F52 (8-byte encrypted PIN block) + F53 (8-byte KSN), chip+PIN | field39=00 | All (mock HSM passes through) |
| `emv-chip-arqc-approved` | F55 with BER-TLV: Tag 9F26 (ARQC) + Tag 9F36 (ATC=1) | field39=00 **and** field91_hex present (16 hex chars = 8-byte ARPC) | All |
| `emv-chip-arqc-invalid` | F55 with tampered ARQC (last byte flipped) | field39=00 (mock) / field39=82 (staging with SoftHSM2 + payments-imk) | Meaningful only on staging |

> **Note on `emv-chip-arqc-invalid`:** With the mock HSM (`hsm.provider=mock`), ARQC
> verification always passes and the scenario returns `00`. On staging with
> `hsm.provider=softhsm` and the `payments-imk` key loaded, the real EMV Option A
> cryptogram is verified and a tampered ARQC is rejected with `82` (Cryptographic failure).

### Security — adversarial message integrity

| Scenario ID | What it sends | Asserts | Environment |
|---|---|---|---|
| `replay-attack` | Same STAN twice in rapid succession | First field39=00; second field39=94 (duplicate STAN) | All |
| `oversized-field` | Field 48 exceeding declared length limit | Connection closes or field39=30 | All |
| `hmac-bypass` | Valid payload but incorrect MAC in F64 | field39=10 (MAC error) | All |
| `pan-in-logs-check` | Normal auth, then log scrape | PAN must not appear in application logs | All |
| `sql-injection-merchantid` | SQL injection string in merchant ID field | field39=30; no DB error in logs | All |

### Concurrency

| Scenario ID | What it sends | Asserts | Environment |
|---|---|---|---|
| `50-concurrent-auths-same-card` | 50 parallel auth requests for the same PAN | No duplicate approvals; all responses coherent | All |
| `auth-reversal-race` | Auth and reversal fired simultaneously with same STAN | Exactly one wins; no orphaned transaction | All |

### Infrastructure — chaos injection

| Scenario ID | What it injects | Asserts | Environment |
|---|---|---|---|
| `circuit-breaker-open` | Network adapter forced open via chaos API | field39=91 (Switch inoperative) | All |
| `kafka-disconnect` | Kafka broker paused | Auth still completes; event published after reconnect | All |
| `postgres-slow` | DB latency injected (500 ms+) | Auth completes within timeout; no 500 error | All |
| `redis-down` | Redis container stopped | Auth completes (cache miss, fallback to DB) | All |

### Network Security ⬜ planned

> Requires staging environment with real TLS certs. Not runnable in local dev.

| Scenario ID | What it tests | Asserts | Environment |
|---|---|---|---|
| `tls-expired-cert` | Acquiring REST endpoint called with an expired client cert | Connection refused / TLS handshake failure | Staging |
| `mtls-missing-cert` | Internal service call without client certificate | 403 or connection reset | Staging |
| `cert-rotation-zero-downtime` | Cert rotated mid-traffic; requests before and after rotation captured | All requests succeed; no connection gap | Staging |

### Routing & Resilience ⬜ planned

| Scenario ID | What it tests | Asserts | Environment |
|---|---|---|---|
| `unknown-bin-fallback` | BIN with no primary route; secondary route defined | Routed to secondary; field39=00 | Staging |
| `visa-upstream-down-mc-fallback` | Visa mock upstream returns 503; Mastercard PAN routed via fallback | field39=00 via fallback; circuit breaker counter incremented | Staging |
| `round-robin-multi-instance` | Auth request sent 10 times; checked against 2 acquiring-service instances | Requests distributed across both instances (verified via MDC traceId in logs) | Staging |

### Data Consistency ⬜ planned

> These scenarios require a `DatabaseAssertionAdapter` (planned) that connects to Postgres
> and reads back the transaction row after the ISO 8583 response.

| Scenario ID | What it tests | Asserts | Environment |
|---|---|---|---|
| `auth-persisted-correctly` | Normal Visa auth | DB row: status=AUTHORIZATION_PENDING, pan_hash set, amount_paise correct, stan matches | All |
| `reversal-marks-reversed` | Auth followed by reversal | Transaction row status=REVERSED after reversal 0430 response | All |
| `audit-log-written` | Any state transition | audit_log table has one row with transaction_id, old_status, new_status, actor | All |
| `pan-never-in-plaintext` | Auth with real-looking PAN | transactions.pan_hash is SHA-256 hex; `pan` column absent or null | All |

### Observability ⬜ planned

| Scenario ID | What it tests | Asserts | Environment |
|---|---|---|---|
| `trace-propagated` | Normal auth with traceparent header injected | Jaeger shows single trace spanning acquiring → mock-upstream; no orphan spans | Local / Staging |
| `mdc-fields-present` | Auth with known merchantId + cardLast4 | Application log line contains `traceId`, `transactionId`, `merchantId`, `cardLast4` — and no full PAN | All |
| `prometheus-counter-incremented` | 5 auths; then Prometheus scrape | `payment_authorization_total` counter incremented by 5; `payment_authorization_latency_seconds` present | All |

### Compliance ⬜ planned

| Scenario ID | What it tests | Asserts | Environment |
|---|---|---|---|
| `pan-hash-only-stored` | Auth completed | Postgres select: no raw PAN in any column on `transactions` table | All |
| `pin-never-in-logs` | Auth with PIN block | Log scan: no occurrence of PIN block hex or raw PIN in any log file | All |
| `rbi-upi-mdr-zero` | UPI P2M transaction | MDR applied = 0.00 (RBI Jan 2020 mandate) | All |
| `atc-replay-rejected` | Same ATC sent twice for same PAN | Second auth declined (field39=63 — security violation) | Staging (SoftHSM2 required) |
| `rbi-afa-2fa-threshold` | CNP auth ≥ ₹2,000 | Acquirer flags AFA required; 3DS ECI present in response | Staging |

### Contract Testing ⬜ planned

> Pact contracts live under `services/acquiring-service/src/test/pact/`. Provider verification
> is triggered via qa-orchestrator's Pact provider endpoint.

| Contract | Consumer | Provider | Status |
|---|---|---|---|
| `acquiring-auth-contract` | payment-switch | acquiring-service | ⬜ planned |
| `webhook-hmac-contract` | merchant-simulator | webhook-dispatcher | ⬜ planned |

### Analytics ⬜ planned

| Scenario ID | What it tests | Asserts | Environment |
|---|---|---|---|
| `pass-trend-chart-renders` | Reports page opened after 10 runs | Trend chart shows 10 data points; pass rate ≥ 80% for golden-path run | All |
| `suite-run-history` | Suite triggered twice | Reports lists both runs with timestamps; second run has newer `completedAt` | All |
| `per-scenario-breakdown` | Boundary run complete | Each boundary scenario has its own row in the report with individual pass/fail | All |

### Frontend E2E — Playwright ⬜ planned (#143)

> Playwright tests activate per-portal as `data-testid` attributes are added (see
> `docs/specs/security.md §11` — attributes are stripped from production builds via SWC).

| Test ID | Portal | Flow | Status |
|---|---|---|---|
| `simulator-happy-path` | frontend-simulator (3000) | Fill card form → submit → see approval screen | ⬜ planned |
| `simulator-declined-card` | frontend-simulator | Submit expired card → see decline message | ⬜ planned |
| `dashboard-transaction-list` | frontend-dashboard (3001) | Navigate to Transactions → see paginated list; filter by merchant | ⬜ planned |
| `ops-settlement-run` | frontend-ops (3002) | Trigger settlement batch → confirm status changes to SETTLED | ⬜ planned |
| `qa-portal-trigger-run` | frontend-qa-portal (3003) | Click Trigger on golden-path-run → watch live SSE steps update | ⬜ planned |

### Offline / FSE ⬜ planned

> FSE = Field Sales Executive. Tests simulate POS terminal behavior during connectivity loss
> (common in tier-2/3 merchant sites in India). Requires the terminal-simulator tool.

| Scenario ID | What it tests | Asserts | Environment |
|---|---|---|---|
| `terminal-disconnect-reconnect` | Acquiring TCP connection dropped mid-auth; terminal reconnects | Transaction not duplicated; correct ISO 8583 response received on reconnect | Staging |
| `offline-queue-flush` | 5 auths queued offline in terminal-simulator; network restored | All 5 flushed in order; no duplicates; each receives field39=00 | Staging |
| `mpos-nfc-tap-approved` | NFC tap via MPOS simulator (Field 22=91, contactless) | field39=00; contactless path exercised (ISO 8583 Field 22 = 0x91) | Staging / BrowserStack |

### Business Domain Flows ⬜ planned

| Scenario ID | What it tests | Asserts | Environment |
|---|---|---|---|
| `emi-conversion-3month` | Auth with EMI flag; 3-month plan selected | field39=00; EMI plan row created with correct installment amount | All |
| `dcc-usd-conversion` | Auth in USD on INR terminal | DCC rate applied; amount converted; original USD amount in Field 54 | Staging |
| `partial-approval-code10` | Issuer mock returns response code 10 with lower approved amount | field39=10; approved_amount < requested_amount; PARTIAL_APPROVAL event published | Staging |
| `auth-hold-expiry` | Auth created; 8-day-old record checked by expiry monitor | Auth status → EXPIRED after monitor run; audit log row present | All |

### Fraud / AI / RAG Pipeline ⬜ planned

| Scenario ID | What it tests | Asserts | Environment |
|---|---|---|---|
| `fraud-score-high-velocity` | 10 auths from same PAN within 60 seconds | LangGraph fraud score > 0.85; transaction flagged REVIEW; Kafka event `fraud.alert` published | Staging |
| `fraud-score-normal` | Single auth; normal amount; known merchant | Fraud score < 0.3; transaction proceeds; no REVIEW flag | All |
| `rag-retrieval-masked-pan` | Settlement PDF ingested; query includes PAN-like string | Retrieved chunk has PAN masked (last 4 only); no raw PAN returned | Staging |
| `prompt-injection-merchant-name` | Merchant name contains `Ignore previous instructions...` | Fraud score computed normally; no prompt injection executed; output is a numeric score | Staging |

### Localization ⬜ planned

| Scenario ID | What it tests | Asserts | Environment |
|---|---|---|---|
| `inr-lakh-format` | Transaction amount ₹1,00,000 displayed in simulator frontend | Amount rendered as `₹1,00,000` (Indian lakh format), not `₹100,000` | All |
| `ist-timestamp` | Auth response timestamp (Field 7) converted for display | UI shows IST (UTC+5:30) timestamp, not UTC | All |
| `upi-daily-limit` | UPI P2M auth ₹1,00,001 (above ₹1L NPCI daily limit) | field39=61 (exceeds withdrawal limit) | Staging |

---

## Pre-configured Runs

A **run** is an ordered list of scenarios with a shared session and variable defaults.

| Run ID | Mode | Scenarios | Purpose |
|---|---|---|---|
| `golden-path-run` | STATEFUL | Visa, RuPay, Mastercard, Amex, Diners, EMV-ARQC, Chip+PIN | Happy-path smoke test; run after every deploy |
| `boundary-run` | STATELESS | All boundary / input rejection scenarios | Validates input rejection; run on PR |
| `security-run` | STATELESS | Replay attack, oversized field, EMV tampered ARQC, MAC bypass | Adversarial; run on PR and nightly |
| `full-lifecycle-run` | STATEFUL | Auth + manual receipt check + reversal | Includes human gate; use for demo or release validation |
| `infrastructure-run` | STATELESS | All chaos scenarios (Kafka, Postgres, Redis, circuit breaker) | Run on staging only; tears down infrastructure |
| `compliance-run` | STATELESS | PAN-hash-only, PIN-never-in-logs, UPI MDR, ATC replay | PCI DSS + RBI compliance checks; run on staging |
| `data-consistency-run` | STATELESS | DB assertions: auth-persisted, audit-log, pan-never-in-plaintext | Cross-layer correctness; run on staging |
| `business-domain-run` | STATELESS | EMI, DCC, partial approval, auth hold expiry | Domain-specific flows; run on staging |

---

## The Full Regression Suite

**`v1-full-suite`** runs all five runs in sequence (`SEQUENTIAL`, `FAIL_FAST`).
Run this before every deployment. A failure in any run stops the suite immediately.

```
golden-path-run → full-lifecycle-run → boundary-run → security-run → infrastructure-run
```

Trigger it from **Suites → v1 full regression suite → Trigger**.

---

## Environment Notes

| Feature | `hsm.provider=mock` (local / CI) | `hsm.provider=softhsm` (staging) |
|---|---|---|
| DUKPT PIN decrypt | Returns mock handle; no actual decryption | Real 3DES DUKPT derivation against loaded BDK |
| ARQC verification | Always returns valid | Verifies against `payments-imk`; rejects tampered cryptograms |
| ARPC generation | Returns 8 zero bytes | Real EMV Option A ARPC under CSK derived from IMK + ATC |

Scenarios that behave differently between environments are marked in the catalog above.

---

## Adding New Scenarios

When a new feature ticket is implemented, add its QA scenarios in the same PR:

1. Create one or more YAML files under:
   ```
   services/qa-orchestrator/src/main/resources/scenarios/
     acquiring-service/payments/<feature>/
   ```
2. Add rows to the appropriate table in this document.
3. Add the scenario IDs to the relevant run YAML(s) under `resources/runs/`.
4. If the run is part of `v1-full-suite`, no suite file change is needed — runs are already included.

The portal picks up new scenarios automatically after the qa-orchestrator service restarts.
No frontend code changes are required.

---

### QR / UPI Payments ✅ implemented

| Scenario ID | What it tests | Asserts | Environment |
|---|---|---|---|
| `dynamic-qr-generate-pending` | `POST /qr/generate` for MERCH0000999, ₹6,000 | 200 response; txnRef starts with TXN; qrImageBase64 non-empty; status=PENDING | All |
| `dynamic-qr-payment-completed` | Generate QR then `POST /upi/credit` with matching txnRef and amount | Credit ack status=COMPLETED; `GET /qr/status` returns COMPLETED; npciTxnId stored | All |
| `qr-session-expired` | `GET /qr/status/{nonexistentTxnRef}` | 404 with reason message | All |

---

*Last updated: 2026-05-21 — #38+#91 Dynamic QR + caching; #36 DUKPT; #37 EMV ARQC/ARPC; AMEX/Diners routing.*
