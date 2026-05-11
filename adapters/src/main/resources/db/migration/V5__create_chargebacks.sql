-- V5: Chargebacks

CREATE TABLE chargebacks (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      UUID            NOT NULL REFERENCES transactions(id),
    arn                 VARCHAR(23),
    network             VARCHAR(20)     NOT NULL,
    reason_code         VARCHAR(10)     NOT NULL,
    amount              NUMERIC(15,2)   NOT NULL,
    chargeback_fee      NUMERIC(10,2)   NOT NULL DEFAULT 350.00,
    status              VARCHAR(30)     NOT NULL DEFAULT 'RECEIVED',
    response_deadline   TIMESTAMPTZ     NOT NULL,
    evidence_s3_key     VARCHAR(500),
    received_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ
);

CREATE INDEX idx_chargebacks_transaction
    ON chargebacks(transaction_id);
