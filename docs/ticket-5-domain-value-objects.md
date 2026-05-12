# Ticket #5 — Domain Value Objects

## What
Seven self-validating Java 21 record-based value objects in `com.payments.domain.model.vo`
that eliminate primitive obsession across the payments domain.

## Why
Closes #5. Using raw `String` or `double` for payment data causes silent bugs — a
16-char merchant ID accepted where 15 is the maximum, or floating-point rounding errors
in amounts that only surface during settlement. Value objects make invalid state
unrepresentable: validation runs once at construction and every downstream caller
receives a guarantee that the value is already correct.

## Design Decisions

- **Records over classes**: Java 21 records give `equals`, `hashCode`, and `toString`
  for free. Compact constructors handle all validation in one place — no separate
  `validate()` method to forget to call.

- **`Money` uses `BigDecimal` + `Currency`**: `double` and `float` use binary
  floating-point internally — `0.1 + 0.2` evaluates to `0.30000000000000004`, not `0.30`.
  At payment scale even one-paisa rounding errors cause settlement discrepancies.
  `BigDecimal` is exact. Scale is normalised to 2 at construction so `6000` and
  `6000.00` are treated as the same value.

- **`PanHash.fromRawPan()` hashes at the boundary**: The domain never holds a raw PAN.
  SHA-256 is computed once on entry and only the hex hash travels through the system.
  `toString()` returns `PanHash{<first8>...}` — safe to log, never leaks card data.

- **`AuthorizationCode` stored as uppercase**: ISO 8583 Field 38 is case-insensitive in
  practice; normalising to uppercase avoids equality surprises downstream.

- **`MerchantId` allows 6–15 alphanumeric chars**: ISO 8583 Field 42 is 15 chars padded,
  but real merchant IDs can be shorter. Floor of 6 prevents degenerate values.

- **`TerminalId` is exactly 8 chars**: ISO 8583 Field 41 is a fixed 8-char field.
  No flexibility — either exactly 8 or construction fails immediately.

- **`AcquirerReferenceNumber` is exactly 23 chars**: Standard ARN format used by
  Visa/MC for dispute matching and chargeback correlation.

- **`SystemTraceAuditNumber` is exactly 6 numeric digits**: ISO 8583 Field 11 spec.
  `intValue()` convenience method avoids repeated `Integer.parseInt` at call sites.

---

## File-by-File Reference

### `Money.java`
**What it represents:** Any monetary amount in the system — ₹6000.00, ₹0.50, etc.

**Why it exists:** `double`/`float` use binary floating-point — `0.1 + 0.2` is
`0.30000000000000004`. One-paisa errors compound at settlement scale into real
financial discrepancies. `BigDecimal` is exact decimal arithmetic.

**What it holds:** A `BigDecimal` amount + a `java.util.Currency`. Scale is forced to
2 decimal places at construction so `6000` and `6000.00` are the same value.

**Key methods:**
- `add(Money other)` — enforces same currency (can't add ₹100 + $5 by mistake),
  returns a new `Money`.
- `subtract(Money other)` — same currency guard, returns a new `Money`.
- Both methods use `RoundingMode.HALF_UP` and preserve 2 decimal places.

---

### `MerchantId.java`
**What it represents:** The unique identifier of a merchant — e.g. `MERCH0000999`.

**Why it exists:** ISO 8583 Field 42 is a 15-character merchant ID field. Using raw
`String` means nothing prevents a 3-char value or a `TerminalId` being passed in the
wrong argument slot — Java method signatures cannot distinguish `String merchantId`
from `String terminalId`. The compiler can distinguish `MerchantId` from `TerminalId`.

**Rules enforced at construction:**
- Not null, not blank.
- 6–15 alphanumeric characters only. Floor of 6 prevents meaningless values.

---

### `TerminalId.java`
**What it represents:** The POS terminal's unique ID — e.g. `TERM0042`.

**Why it exists:** ISO 8583 Field 41 is a fixed 8-character field. The terminal ID is
part of the idempotency key (`STAN + TerminalId + Date`), so a wrong-length value would
silently break duplicate detection by producing a mismatched Redis key.

**Rules enforced at construction:**
- Not null, not blank.
- Exactly 8 alphanumeric characters. Any other length = instant exception.

---

### `PanHash.java`
**What it represents:** The SHA-256 hash of a card's PAN (Primary Account Number — the
16-digit card number printed on the card).

**Why it exists:** PCI-DSS: the raw PAN must never be stored, logged, or passed through
the system after the terminal boundary. The hash gives a stable identifier for fraud
velocity checks (is this card being used at multiple terminals in 5 minutes?) and BIN
lookups, without ever holding the actual card number.

**Key design:**
- `PanHash.fromRawPan(String rawPan)` is the only way to create one — it hashes
  immediately. After this call the raw PAN is gone.
- Direct construction from a pre-computed hash string is also allowed, for loading
  from the database where only the hash was stored.
- `toString()` prints only the first 8 hex characters followed by `...` — safe to
  include in logs without leaking card data.

---

### `AuthorizationCode.java`
**What it represents:** The 6-character approval code returned by the issuing network
when a transaction is approved — stored in ISO 8583 Field 38. Example: `483921`.

**Why it exists:** This code is the proof of authorization — it appears on receipts,
in chargeback evidence packages, and in dispute correspondence with Visa/MC. Storing it
as a raw `String` gives no guarantee it is the correct format, and a wrong-case value
could fail a string equality check silently.

**Rules enforced at construction:**
- Not null, not blank.
- Exactly 6 alphanumeric characters.
- Normalised to uppercase at construction — ISO 8583 treats `abc123` and `ABC123`
  as the same code; normalising prevents equality comparison failures downstream.

---

### `AcquirerReferenceNumber.java` (ARN)
**What it represents:** A 23-character unique reference assigned by the acquirer
(Payswiff) to each transaction. This is the **universal ID** used across networks,
chargebacks, and reconciliation.

**Why it exists:** When Visa files a chargeback they reference the ARN. When the
reconciliation service matches the switch DB against the network's settlement file it
matches by ARN. When ops traces a disputed transaction across services they use the ARN.
A wrong-length value silently breaks all three flows. The type enforces the contract at
construction so there is nowhere to pass a wrong value.

**Rules enforced at construction:**
- Not null.
- Exactly 23 characters.

---

### `SystemTraceAuditNumber.java` (STAN)
**What it represents:** A 6-digit sequential number generated by the terminal for each
transaction within a calendar day — ISO 8583 Field 11. Example: `000042`.

**Why it exists:** The combination of `STAN + TerminalId + Date` is the idempotency
key stored in Redis (TTL 24h). If the same terminal resends the same STAN on the same
day, the acquiring service detects it as a duplicate and returns the cached response
without reprocessing — preventing double charges. A STAN that isn't exactly 6 numeric
digits breaks the key format and idempotency silently stops working.

**Rules enforced at construction:**
- Not null.
- Exactly 6 digits (0–9 only, no letters or special characters).

**Convenience method:** `intValue()` returns the numeric value as an `int` — avoids
`Integer.parseInt(stan.value())` at every call site.

---

## Test Coverage

88 tests across 7 test classes:

| Class | Happy path | Null/blank | Length bounds | Format | Arithmetic / Cross-currency |
|---|---|---|---|---|---|
| `MoneyTest` | ✓ | ✓ | — | — | ✓ |
| `MerchantIdTest` | ✓ | ✓ | ✓ | ✓ | — |
| `TerminalIdTest` | ✓ | ✓ | ✓ | ✓ | — |
| `PanHashTest` | ✓ | ✓ | ✓ | ✓ | — |
| `AuthorizationCodeTest` | ✓ | ✓ | ✓ | ✓ | — |
| `AcquirerReferenceNumberTest` | ✓ | ✓ | ✓ | ✓ | — |
| `SystemTraceAuditNumberTest` | ✓ | ✓ | ✓ | ✓ | — |

JaCoCo line coverage: all checks passed (≥90% threshold on `com.payments.domain.*`).

## How to Verify

```bash
mvn test -pl domain
# Expected: Tests run: 88, Failures: 0, Errors: 0, Skipped: 0
# Expected: All coverage checks have been met.
# Expected: BUILD SUCCESS
```
