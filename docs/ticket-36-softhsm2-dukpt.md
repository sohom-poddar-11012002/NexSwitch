# Ticket #36 — SoftHSM2 PKCS#11: DUKPT Key Hierarchy, PIN Block Decrypt + ZPK Re-encrypt

## What
Implemented a real PKCS#11-backed HSM adapter using SoftHSM2, replacing the mock for staging environments. Added the full ANSI X9.24-2004 DUKPT (Derived Unique Key Per Transaction) key derivation algorithm and wired PIN block translation into the authorization flow.

## Why
PCI-DSS requires that PIN blocks are never in plaintext outside an HSM boundary. DUKPT ensures that even if one terminal transaction key is compromised, all past and future keys remain safe (forward secrecy). This is a non-negotiable requirement for any acquirer processor — Juspay, Pine Labs, and Payswiff all operate exactly this flow.

## Design Decisions

**DUKPT algorithm — pure Java, not jPOS SM**
PKCS#11 has no native DUKPT command. We implement ANSI X9.24-2004 3DES DUKPT in `DukptKeyDerivation` using JCE primitives. The BDK is stored in the SoftHSM2 token as a PKCS#11 key object; we extract it for DUKPT derivation (SoftHSM2 allows extraction for dev — real hardware HSMs like Thales use vendor-specific `A6` commands instead). The resulting transaction key decrypts the PIN block inside the HSM session boundary.

**`SoftHsm2Session` — SunPKCS11 inline config**
Uses JDK-bundled `SunPKCS11` provider with inline `--` config string (JDK 9+ style). One provider instance per slot; removed from Security on `@PreDestroy`. The PKCS11 KeyStore gives us key handles — `getEncoded()` works on extractable SoftHSM2 keys, returns null on real hardware.

**Conditional wiring — `HsmAdapterConfig` in the adapters module**
`hsm.provider=softhsm` activates `SoftHsm2HsmAdapter`; `hsm.provider=mock` (default) keeps `MockHsmAdapter`. The wiring is in `HsmAdapterConfig` (adapters module) — not `AdapterConfig` (application module) — to avoid ArchUnit rule 9 (application must not import adapter implementations).

**`AuthorizationCommand.ksn` added**
Field 53 (KSN, 10 bytes) is now extracted from the ISO 8583 message and carried into the domain service. `translatePinBlock()` is called in step 6a of `AuthorizationService.execute()` when both `pinBlock` and `ksn` are non-null — guards against non-PIN flows (contactless, QR).

**ARQC + MAC in `SoftHsm2HsmAdapter`**
`verifyArqc` implements EMV Option A card session key derivation (IMK + ATC → CSK → 3DES-MAC). `verifyMac` implements 3DES-CBC-MAC for ISO 8583 Field 64. Both are fully implemented (not stubs) but depend on IMK and MAK being loaded in the token — gracefully skipped if the `payments-imk` alias is absent.

## Test Coverage
- `DukptKeyDerivationTest` — 9 tests:
  - IPEK matches ANSI X9.24-2004 known vector (`6AC292FAA1315B4D858AB3A3D7D5933A`)
  - Counter=0 → transaction key = IPEK (no derivation)
  - Counter=1 → derived transaction key verified
  - PIN encryption key variant correctly applied
  - Round-trip encrypt/decrypt recovers plaintext PIN block
  - Different counters produce different keys (forward secrecy property)
  - Non-reversible key generation produces fixed-size result
  - BDK_VARIANT bit pattern verified

## How to Verify

**With mock (default — CI/local dev):**
```bash
mvn test -pl domain,adapters
# 557 tests, all green
```

**With SoftHSM2 (staging/demo):**
```bash
# Install and initialize
apt-get install softhsm2
softhsm2-util --init-token --slot 0 --label "payments-hsm" --pin 1234 --so-pin 5678

# Import test BDK
softhsm2-util --import bdk.p12 --label payments-bdk --id 01 --slot 0 --pin 1234

# Start acquiring-service with softhsm profile
HSM_PROVIDER=softhsm \
HSM_LIBRARY=/usr/lib/softhsm/libsofthsm2.so \
HSM_PIN=1234 \
java -jar acquiring-service.jar
```

Then send an ISO 8583 0100 with Field 52 (DUKPT-encrypted PIN block) and Field 53 (KSN) — the adapter derives the transaction key, decrypts, and re-encrypts under ZPK.
