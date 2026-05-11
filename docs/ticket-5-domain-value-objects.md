# Ticket #5 — Domain Value Objects

## What
Seven self-validating Java 21 record-based value objects in `com.payments.domain.model.vo`
that eliminate primitive obsession across the payments domain.

## Why
Closes #5. Primitive strings and doubles for payment data lead to silent bugs — a 16-char
merchant ID accepted where a 15-char one is required, or floating-point rounding errors
in amounts. Value objects make invalid state unrepresentable at construction time.

## Design Decisions

- **Records over classes**: Java 21 records give `equals`, `hashCode`, and `toString` for
  free. Compact constructors handle all validation in one place.

- **`Money` uses `BigDecimal` + `Currency`**: Never `double`. Scale is normalised to 2 at
  construction. Arithmetic methods (`add`, `subtract`) enforce same-currency invariant.

- **`PanHash.fromRawPan()` hashes at the boundary**: The domain never holds a raw PAN.
  `SHA-256` is computed once on entry and only the hex hash travels through the system.
  `toString()` returns `PanHash{<first8>...}` — safe for logs.

- **`AuthorizationCode` stored as uppercase**: ISO 8583 Field 38 is case-insensitive in
  practice; normalising to uppercase avoids equality surprises downstream.

- **`MerchantId` allows 6–15 alphanumeric chars**: ISO 8583 Field 42 is 15 chars padded,
  but real merchant IDs can be shorter. Floor of 6 prevents degenerate values.

- **`TerminalId` is exactly 8 chars**: ISO 8583 Field 41 is a fixed 8-char field.

- **`AcquirerReferenceNumber` is exactly 23 chars**: Standard ARN format used by
  Visa/MC for dispute matching and chargeback correlation.

- **`SystemTraceAuditNumber` is exactly 6 numeric digits**: ISO 8583 Field 11 spec.
  `intValue()` convenience method avoids repeated `Integer.parseInt` at call sites.

## Test Coverage

88 tests across 6 test classes (ArchUnit adds the remaining tests):

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
