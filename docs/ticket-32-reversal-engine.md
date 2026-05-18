# Ticket #32 — Reversal Engine

Closes #32.

## What

Timeout-triggered reversal flow: when an ISO 8583 0100 authorization request receives no 0110 response within 15 seconds, the system transitions the transaction to `REVERSAL_PENDING`, sends a 0400 reversal advice to the network, and settles in `REVERSED` or `UNKNOWN`.

## Why

ISO 8583 mandates that an acquirer must send a reversal (MTI 0400) if no authorization response is received within the terminal timeout window. Without this, the cardholder's limit may be blocked indefinitely even though no funds were transferred.

## Design Decisions

- **Race condition guard via domain state machine** — `Transaction.initiateReversal()` calls `canTransitionTo(REVERSAL_PENDING)`. If a concurrent 0110 response already moved the transaction to `AUTHORIZED`/`DECLINED`, the guard throws `InvalidStateTransitionException` which `ReversalService` catches and maps to `AlreadyReversed`/`Failed` without any DB write. No `SELECT FOR UPDATE` needed at the domain layer.
- **`AuthorizationPort.reverse()` reused** — the 0400 uses the same outbound port as the 0100; no separate `ReversalPort` needed since both go to the same upstream network connection.
- **`@Scheduled(fixedDelay)` not `fixedRate`** — `fixedDelay` ensures the next scan starts after the previous one completes, preventing overlapping scans under load.
- **Best-effort swallow in monitor** — `TimeoutReversalMonitor` catches all exceptions per transaction and logs them. Throwing from `@Scheduled` would cancel all future runs in Spring.
- **`findAuthorizationPendingOlderThan(Instant)`** added to `TransactionRepository` port — the threshold is computed by the caller (adapter layer); the port stays pure and testable without `Duration` logic.

## Test Coverage

| Test | Scenario |
|---|---|
| `givenAuthorizationPending_whenReverse_thenReturnsAccepted` | Happy path from AUTHORIZATION_PENDING |
| `givenAuthorized_whenReverse_thenReturnsAccepted` | Happy path from AUTHORIZED |
| `givenNetworkReversalFails_thenUnknownStateAndReturnsFailed` | Network returns Failed → UNKNOWN |
| `givenTransactionNotFound_thenReturnsFailed` | Missing transaction ID |
| `givenAlreadyReversed_thenIdempotentAlreadyReversed` | Idempotent retry |
| `givenRaceCondition_concurrentAuthorizationReceived_thenAlreadyReversed` | Concurrent 0110 received before reversal fires |
| `givenNetworkAlreadyReversed_thenTransitionToReversedAndReturnAlreadyReversed` | Idempotent network response |

7 new tests. Domain total: 500, 0 failures.

## How to Verify

1. Start the stack: `docker compose up -d`
2. Send a 0100 from `terminal-simulator` with the mock upstream configured to never respond
3. After 15 seconds the `TimeoutReversalMonitor` scan fires; observe log line `timeout reversal triggered`
4. Query the transaction — status should be `REVERSED`
5. Confirm no second 0400 is sent if the monitor fires again (idempotency guard)
