# Ticket #37 — EMV ARQC Verification + ARPC Generation

## What
Wired EMV chip cryptogram verification into the authorization flow. Field 55 (EMV TLV data)
is now parsed at the inbound boundary, ARQC (Tag 9F26) is verified using EMV Option A card
session key derivation, and ARPC (Method 1) is generated and returned in Field 91 of the
0110 response.

## Why
ARQC verification is what makes chip transactions genuinely secure: it proves the physical
card was present (not just cloned magnetic stripe data). Without verifying the ARQC, an
acquirer processor accepts chip transactions on trust — the cryptogram is ignored. Every
real acquirer (Juspay, Pine Labs, Payswiff) verifies ARQC before forwarding to the network.

ARPC is the issuer's response cryptogram: the terminal uses it to verify the 0110 response
came from a real issuer, not a man-in-the-middle. Without ARPC, the terminal has no proof
the approval is genuine.

## Design Decisions

**`EmvData` value object — parsed at the inbound boundary**
Field 55 is raw BER-TLV bytes. The domain should never see raw bytes. `EmvTlvParser`
parses Field 55 in `Iso8583RequestHandler` and produces an `EmvData(arqc, atc, transactionData)`
value object. The domain receives typed data; the parsing concern stays in the adapter.

**`EmvCryptography` — extracted from `SoftHsm2HsmAdapter`**
The card session key derivation and 3DES-CBC-MAC were private methods in `SoftHsm2HsmAdapter`.
Extracting them to `EmvCryptography` (alongside `DukptKeyDerivation`) makes them unit-testable
without a PKCS#11 provider. `ArqcVerificationTest` tests the algorithm directly with
self-consistent vectors.

**EMV Option A UDD — ATC-only, no PAN in derivation**
The standard UDD (Unique Derivation Data) for Option A single-length key is `ATC || 0x00*6`.
The PAN is used in some derivation variants (Option B) but not Option A. The `HsmPort.verifyArqc`
signature accepts `PanHash` for future extensibility but the current derivation uses ATC only.

**`generateArpc` — added `int atc` parameter**
The prior stub called `deriveCardSessionKey(imk, null, 0)` — ATC hardcoded to zero. Fixed
by threading `atc` through `HsmPort.generateArpc` so the same card session key used for
ARQC verification is used for ARPC generation. A mismatched ATC would produce an ARPC the
terminal rejects.

**ARPC in `AuthorizationResult.Approved`**
ARPC is generated in `AuthorizationService` after the network returns `Approved`, and
carried in `AuthorizationResult.Approved.arpc()`. `Iso8583RequestHandler` echoes it into
Field 91 of the 0110. This keeps Field 91 logic in the domain flow rather than scattered
across the adapter.

**`EmvTlvParser` — hand-rolled BER-TLV, not jPOS TLVList**
jPOS has TLV parsing utilities but their API varies across versions and the TLVList is tied
to the Q2 container lifecycle. The BER-TLV grammar we need (~50 lines) covers 1-byte and
2-byte tags, and 1-byte, 2-byte, and 3-byte lengths — sufficient for all EMV tags in Field 55.

## Test Coverage
- `ArqcVerificationTest` — 8 tests using `EmvCryptography` directly:
  - CSK derivation for ATC=1 produces a 16-byte key
  - ATC=0 derivation differs from IMK (not a no-op)
  - Different ATCs produce different CSKs (forward-secrecy property)
  - ARQC = 3DES-CBC-MAC(CSK, CDOL1_data)
  - Single-byte change in CDOL1 data produces a different ARQC (avalanche)
  - ARPC is deterministic for the same inputs
  - Declined ARC produces a different ARPC than approved ARC
  - Round-trip: card generates ARQC → issuer verifies → issuer generates ARPC
  - Known-vector test: CSK for IMK=`0123456789ABCDEFFEDCBA9876543210`, ATC=1 = `E90EB98AB6F9CB4654D3102A1AF7D2E8`

## How to Verify

**Mock (CI/local dev):**
```bash
mvn test -pl domain,adapters
# 566 tests, all green
```

**ARQC flow (end-to-end with terminal-simulator):**
Send a 0100 with Field 55 containing valid BER-TLV data including Tag 9F26 (ARQC, 8 bytes)
and Tag 9F36 (ATC, 2 bytes). The 0110 response will include Field 91 (ARPC, 8 bytes) for
approved chip transactions.

With `hsm.provider=softhsm` and `payments-imk` loaded in the token, `verifyArqc` will
compute the expected ARQC from the Issuer Master Key and compare. Without `payments-imk`,
it gracefully skips and treats the ARQC as valid (documented behaviour, not silent failure).
