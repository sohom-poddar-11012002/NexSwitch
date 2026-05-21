# QA Portal — Usage Guide

The QA Portal is the browser-based interface for running, monitoring, and reviewing
automated tests against the NexSwitch acquiring service. It connects to the
**qa-orchestrator** backend, which executes scenarios over a live ISO 8583 TCP connection
to the acquiring service.

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

---

## Pre-configured Runs

A **run** is an ordered list of scenarios with a shared session and variable defaults.

| Run ID | Mode | Scenarios | Purpose |
|---|---|---|---|
| `golden-path-run` | STATEFUL | Visa, RuPay, Mastercard, Amex, Diners, EMV-ARQC, Chip+PIN | Happy-path smoke test; run after every deploy |
| `boundary-run` | STATELESS | All boundary scenarios | Validates input rejection; run on PR |
| `security-run` | STATELESS | Replay attack, oversized field, EMV tampered ARQC | Adversarial; run on PR and nightly |
| `full-lifecycle-run` | STATEFUL | Auth + manual receipt check + reversal | Includes human gate; use for demo or release validation |
| `infrastructure-run` | STATELESS | All chaos scenarios | Run on staging only; tears down infrastructure |

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

*Last updated: 2026-05-21 — tickets #36 (DUKPT), #37 (EMV ARQC/ARPC), AMEX/Diners routing.*
