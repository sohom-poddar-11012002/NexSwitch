# Ticket #18 — Golden Path

Closes #18.

## What

Wired `Iso8583RequestHandler` to `ProcessPaymentUseCase` so an incoming MTI 0100 from the terminal triggers the real 8-step `AuthorizationService` pipeline and the result is mapped back to Field 39 in the 0110 response.

## Why

Week 2 milestone — the full chain terminal → switch → network → terminal must work end-to-end. Without this wire-up, the ISO 8583 inbound adapter was a stub that never called any business logic.

## Design Decisions

- **Luhn check at the adapter boundary** — PAN validation belongs in the inbound adapter (`Iso8583RequestHandler`), not in `AuthorizationService`. The domain layer should never see an invalid PAN; `Field 39 = "14"` (invalid card number) is returned without calling the use case.
- **PAN never travels past the boundary in plaintext** — bin6 is extracted first, then PAN is hashed to `PanHash`. The domain only receives the hash and the first 6 digits.
- **`PaymentNetwork` inferred from MII** — Major Industry Identifier (first digit of PAN) maps to the network: `4` → VISA, `5` → MASTERCARD, `6` → RUPAY, `3` → AMEX/DINERS. This avoids a BIN lookup for routing purposes at the inbound layer.
- **`PaymentMethod` derived from POS Entry Mode (Field 22)** — `05` = chip, `02`/`90` = MSR swipe, `07` = contactless. Adapter translates the ISO code to the domain enum.
- **Sealed `AuthorizationResult` switch** — compiler enforces exhaustive mapping to Field 39 codes: `Approved`→"00", `Declined`→responseCode, `Unknown`→"91", `Blocked`→"59". No default case.
- **`Iso8583ChannelInitializer` accepts `ProcessPaymentUseCase`** — Spring auto-injects the `AuthorizationService` bean; no `AdapterConfig` changes needed since `AuthorizationService` is already declared there.

## Test Coverage

| Test | Scenario |
|---|---|
| Luhn fail | Field 39 = "14", use case never called |
| Approved | Field 39 = "00", auth code echoed |
| Declined | Field 39 = response code from domain |
| Blocked (fraud) | Field 39 = "59" |
| Switch inoperative (Unknown) | Field 39 = "91" |
| Command capture | bin6, stan, terminalId, merchantId, amount, network, paymentMethod all correctly extracted |

11 tests total (6 existing + 5 new).

## How to Verify

1. `docker compose up -d`
2. Run `terminal-simulator` with a Luhn-valid Visa PAN (e.g. `4539148803436467`)
3. Terminal prints `APPROVED — Auth code: 000000`
4. Postgres `transactions` table shows `status = AUTHORIZED`
