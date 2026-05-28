-- V22: Network tokenization table (N76)
-- LEARN: Network tokenization (EMVCo) — Visa (VTS) and Mastercard (MDES) replace the PAN
--        with a token that is valid for a specific merchant and device. If the token is stolen
--        it cannot be used at another merchant, eliminating PAN-level card fraud at scale.
CREATE TABLE network_tokens (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id          VARCHAR(20)  NOT NULL,
    network_token_value  VARCHAR(64)  NOT NULL UNIQUE,
    last_four            VARCHAR(4)   NOT NULL,
    network              VARCHAR(20)  NOT NULL,
    expiry               DATE         NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_network_tokens_merchant ON network_tokens(merchant_id);
CREATE INDEX idx_network_tokens_status   ON network_tokens(status) WHERE status = 'ACTIVE';
