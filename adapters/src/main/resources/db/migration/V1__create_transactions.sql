-- V1: Core transactions table
-- NEVER float/double for money — NUMERIC(15,2) only
-- PAN stored as SHA-256 hash only, never plaintext

CREATE TABLE transactions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    stan                VARCHAR(6)      NOT NULL,
    rrn                 VARCHAR(12),
    arn                 VARCHAR(23),
    terminal_id         VARCHAR(8)      NOT NULL,
    merchant_id         VARCHAR(15)     NOT NULL,
    pan_hash            VARCHAR(64)     NOT NULL,     -- SHA-256, NEVER store PAN
    card_last4          VARCHAR(4),
    network             VARCHAR(20),
    payment_method      VARCHAR(30)     NOT NULL,
    amount              NUMERIC(15,2)   NOT NULL,     -- NEVER float/double
    currency            VARCHAR(3)         NOT NULL DEFAULT 'INR',
    status              VARCHAR(30)     NOT NULL,
    authorization_code  VARCHAR(6),
    response_code       VARCHAR(2),
    risk_score          VARCHAR(10),
    idempotency_key     VARCHAR(100)    UNIQUE NOT NULL,
    npci_txn_id         VARCHAR(50),
    qr_txn_ref          VARCHAR(50),
    version             BIGINT          NOT NULL DEFAULT 0,   -- optimistic locking
    upstream_request_at TIMESTAMPTZ,
    upstream_response_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    settled_at          TIMESTAMPTZ,
    reconciled_at       TIMESTAMPTZ,
    paid_out_at         TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_transactions_idempotency
    ON transactions(idempotency_key);

CREATE INDEX idx_transactions_merchant_status_date
    ON transactions(merchant_id, status, created_at DESC);

CREATE INDEX idx_transactions_settlement_pending
    ON transactions(status, created_at)
    WHERE status IN ('CAPTURED', 'SETTLEMENT_PENDING');

CREATE INDEX idx_transactions_arn
    ON transactions(arn)
    WHERE arn IS NOT NULL;

CREATE INDEX idx_transactions_qr_ref
    ON transactions(qr_txn_ref)
    WHERE qr_txn_ref IS NOT NULL;

CREATE TABLE transaction_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID        NOT NULL REFERENCES transactions(id),
    from_status     VARCHAR(30),
    to_status       VARCHAR(30) NOT NULL,
    actor_service   VARCHAR(50) NOT NULL,
    event_data      JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transaction_events_txn
    ON transaction_events(transaction_id, occurred_at DESC);
