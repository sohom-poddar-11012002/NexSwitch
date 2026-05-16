# Ticket #27 — QRSessionManager Domain Service

## What
`QRSessionManager` domain service: creates `QRSession` objects, builds UPI deep-link
strings, and checks whether a session is still active.

## Why
Closes #27. `QRSessionManager` was listed in §3 (project structure) and referenced by
`GenerateQRUseCase` but omitted when the QR port/model work was shipped in ticket #10.
The service is required before any QR use-case implementation in adapters or application.

## Design Decisions

- **Constructor-injected TTL**: `sessionTtlMinutes` is passed at construction rather than
  read from `@Value`. Domain is Spring-free; application config wires the value in
  `AdapterConfig` (or a future `QRConfig` bean). Default for all tests: 5 minutes.

- **`AtomicInteger` sequence counter**: The `txnRef` format (`TXN{yyyyMMddHHmmss}{merchantId}{seq}`)
  requires a monotonic per-instance counter. An `AtomicInteger` is sufficient for single-JVM
  uniqueness; the timestamp + merchantId prefix prevents collisions across restarts or instances.

- **`includes` added to JaCoCo `prepare-agent` (domain/pom.xml)**: `QRSessionManager` declares
  a static `DateTimeFormatter` that triggers JDK locale class loading at test runtime. JaCoCo
  0.8.13 cannot instrument Java 26 class files (version 70) and threw
  `IllegalArgumentException: Unsupported class file major version 70` on
  `sun/text/resources/cldr/ext/FormatData_en_IN`. Adding `<includes>com/nexswitch/**</includes>`
  limits instrumentation to project classes only, fixing the issue without downgrading the JVM.

- **URL encoding via `java.net.URLEncoder`**: Part of the JDK standard library; no external
  dependency added. The UPI string spec (§21.1) requires percent-encoded VPA and payee name.

- **`isActive` = PENDING && !expired**: A session in any non-PENDING state (COMPLETED,
  EXPIRED, FAILED) is inactive regardless of the expiry timestamp.

## Test Coverage

`QRSessionManagerTest` (11 tests):
- `create` — correct fields, txnRef contains merchantId, TTL matches configured minutes,
  sequential calls produce unique refs, zero TTL rejected in constructor
- `buildUpiString` — all mandatory components present and URL-encoded, amount formatted
  to exactly 2 decimal places
- `isActive` — returns true for fresh PENDING session; returns false for COMPLETED,
  EXPIRED status; returns false when `expiresAt` is in the past (even if status is PENDING)

## How to Verify

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-26.jdk/Contents/Home \
  mvn clean test -pl domain --no-transfer-progress
# Expected: Tests run: 482, Failures: 0, Errors: 0, Skipped: 0
# All coverage checks have been met.
```
