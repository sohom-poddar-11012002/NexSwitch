-- V14: Merchant disputes
-- Raised when a merchant's end-of-day POS report differs from our settlement statement.
-- Ops investigates against our audit_log; resolution recorded here.

CREATE TABLE merchant_disputes (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id         VARCHAR(15)   NOT NULL REFERENCES merchants(id),
    transaction_id      UUID          REFERENCES transactions(id),
    merchant_reference  VARCHAR(100),
    claimed_amount      NUMERIC(15,2),
    claimed_status      VARCHAR(30),
    our_status          VARCHAR(30),
    evidence_url        VARCHAR(500),
    resolution          VARCHAR(20),      -- MERCHANT_CORRECT | PAYSWIFF_CORRECT | PENDING
    resolution_notes    TEXT,
    raised_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ
);

CREATE INDEX idx_merchant_disputes_merchant   ON merchant_disputes(merchant_id, raised_at DESC);
CREATE INDEX idx_merchant_disputes_txn        ON merchant_disputes(transaction_id) WHERE transaction_id IS NOT NULL;
CREATE INDEX idx_merchant_disputes_unresolved ON merchant_disputes(merchant_id) WHERE resolution = 'PENDING' OR resolution IS NULL;
