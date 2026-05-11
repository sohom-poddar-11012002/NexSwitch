-- V6: Settlement batches

CREATE TABLE settlement_batches (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    network             VARCHAR(20)     NOT NULL,
    batch_date          DATE            NOT NULL,
    batch_file_s3_key   VARCHAR(500),
    transaction_count   INT             NOT NULL,
    gross_amount        NUMERIC(15,2)   NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'GENERATED',
    submitted_at        TIMESTAMPTZ,
    network_batch_id    VARCHAR(100),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_settlement_batches_network_date
    ON settlement_batches(network, batch_date);
