-- V24: Cryptographic key registry (N84)
-- LEARN: Key management lifecycle — ZMK (Zone Master Key) wraps ZPK; ZPK encrypts PINs in transit;
--        MAK (MAC Key) signs ISO 8583 messages; BDK (Base Derivation Key) seeds DUKPT.
--        Tracking expiry here enables automated pre-rotation alerts (findExpiringSoon port).
CREATE TABLE cryptographic_key_registry (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    key_type    VARCHAR(20)  NOT NULL,  -- ZMK, ZPK, MAK, BDK
    key_alias   VARCHAR(100) NOT NULL UNIQUE,
    hsm_slot    INTEGER,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    rotated_at  TIMESTAMPTZ,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_crypto_key_expires ON cryptographic_key_registry(expires_at)
    WHERE status = 'ACTIVE';
