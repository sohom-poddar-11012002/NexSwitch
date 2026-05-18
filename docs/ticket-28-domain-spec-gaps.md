# Ticket #28 — Domain Spec Gap Bridge

Closes #28.

## What

Post-merge audit against the spec revealed 6 gaps in the domain module shipped across tickets #7–#11. All fixes are pure Java — no Spring, no infrastructure.

## Why

The initial implementations matched the acceptance criteria of their individual tickets but missed cross-cutting spec requirements that only became visible when reading the full spec end-to-end after merge. Shipping these as a single fix PR keeps the domain complete before adapter-layer work begins.

## Design Decisions

### BinInfo — three-tier routing fields
- Added `issuerBank` (String) for on-us detection (`"CANARA"`, `"SBI"`, etc.)
- Added `nfsEligible` (boolean) for RuPay/co-badged cards subject to RBI IBL mandate

### NetworkRoute sealed interface (new)
- Models the three routing tiers as domain values: `OnUs(network, bankCode)` | `Ibl(network)` | `Gateway(network)`
- Replaces the previous raw `PaymentNetwork` return from `RoutingEngine`
- Sealed interface means the adapter layer is forced to handle all three tiers at compile time

### RoutingEngine — three-tier logic
- Added `route(BinInfo, String acquirerBank)` implementing priority: on-us → IBL → gateway
- Tier 1 (on-us): `issuerBank == acquirerBank` (case-insensitive) — no interchange, no network fee
- Tier 2 (IBL): `countryCode == "IN" && nfsEligible` — NPCI NFS, RBI mandate
- Tier 3 (gateway): fallback to VisaNet / Banknet

### FraudEngine — ML port wiring
- Added `Optional<FraudScoringPort>` constructor injection; absent by default (fail-open)
- ML score fetched with 10ms budget after inline rule engine runs
- ML skipped entirely when rule engine already returns BLOCK — rule gate always wins, no wasted latency

### FeeBreakdown + FeeWaterfallCalculator — DCC FX margin
- `FeeBreakdown` carries `Optional<Money> dccFxMargin`
- `calculateWithDcc()` overload adds DCC margin (applied on gross amount) as separate Payswiff revenue line above the normal waterfall; base fees unchanged

### MerchantProfile — reserve percentage
- Added `BigDecimal reservePercentage` field used by `FeeWaterfallCalculator` for per-merchant reserve withholding

### HsmPort — DUKPT two-step explicit signatures
- Added `dukptDecrypt(byte[] encryptedPinBlock, byte[] ksn)` → opaque session handle
- Added `reencryptUnderZpk(String plaintextPinBlockHandle, String zpkHandle)` → Field 52 under ZPK
- Existing `translatePinBlock` kept as atomic convenience wrapper for single-hop PIN transit
- Explicit two-step is mandatory for PCI-DSS: plaintext PIN must never leave the HSM boundary between the two operations

## Test Coverage

471 tests total (was 445), all green, JaCoCo gate passes.

| Area | New tests |
|---|---|
| `BinInfo` | 6 |
| `RoutingEngine` three-tier | 13 |
| `FraudEngine` ML port | 6 |
| `FeeWaterfallCalculator` DCC | 5 |
| `MerchantProfile` reserve | 1 |

## How to Verify

```
mvn test -pl domain
# All 471 tests pass, JaCoCo ≥ 90%
```
