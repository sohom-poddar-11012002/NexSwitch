-- V13: Four-party reconciliation detail tables
-- Supports the full 8-step reconciliation job (§4.8):
--   network file ingestion, bank statement ingestion, four-way match,
--   exception categorisation, merchant statement generation.

-- Network settlement file records (one row per transaction line in Visa/MC/NPCI file)
CREATE TABLE reconciliation_network_records (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id                  UUID          NOT NULL REFERENCES reconciliation_runs(id),
    network                 VARCHAR(20)   NOT NULL,
    arn                     VARCHAR(23)   NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL,
    interchange_charged     NUMERIC(10,2),
    network_response_code   VARCHAR(2),
    transaction_date        DATE          NOT NULL,
    batch_id                VARCHAR(50),
    raw_line                TEXT,
    matched                 BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recon_network_arn        ON reconciliation_network_records(arn);
CREATE INDEX idx_recon_network_run_match  ON reconciliation_network_records(run_id, matched);

-- Bank MT940 statement records (one row per credit line in the bank statement)
CREATE TABLE reconciliation_bank_records (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id          UUID          NOT NULL REFERENCES reconciliation_runs(id),
    value_date      DATE          NOT NULL,
    credit_amount   NUMERIC(15,2) NOT NULL,
    reference       VARCHAR(100),           -- network batch ID or ARN
    narrative       TEXT,
    matched         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recon_bank_run_match ON reconciliation_bank_records(run_id, matched);

-- Per-merchant per-day settlement statement (what merchants download)
CREATE TABLE merchant_settlement_statements (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id             VARCHAR(15)   NOT NULL REFERENCES merchants(id),
    statement_date          DATE          NOT NULL,
    transaction_count       INT           NOT NULL DEFAULT 0,
    gross_amount            NUMERIC(15,2) NOT NULL DEFAULT 0,
    interchange_deducted    NUMERIC(10,2) NOT NULL DEFAULT 0,
    mdr_deducted            NUMERIC(10,2) NOT NULL DEFAULT 0,
    net_amount              NUMERIC(15,2) NOT NULL DEFAULT 0,
    reserve_withheld        NUMERIC(10,2) NOT NULL DEFAULT 0,
    payout_due              NUMERIC(15,2) NOT NULL DEFAULT 0,
    s3_key                  VARCHAR(500),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (merchant_id, statement_date)
);

-- Reconciliation exceptions with severity and category
CREATE TABLE reconciliation_exceptions (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id          UUID          NOT NULL REFERENCES reconciliation_runs(id),
    category        VARCHAR(40)   NOT NULL,  -- MISSING_IN_NETWORK | MISSING_IN_SWITCH | MISSING_IN_BANK |
                                             -- AMOUNT_MISMATCH | DUPLICATE_IN_NETWORK | BATCH_AMOUNT_MISMATCH |
                                             -- UNKNOWN_RESOLVED | CHARGEBACK_NOT_IN_SWITCH | UTR_NOT_RECEIVED |
                                             -- RESERVE_BALANCE_MISMATCH | MERCHANT_DISPUTE | PAYOUT_AMOUNT_WRONG
    severity        VARCHAR(10)   NOT NULL,  -- CRIT | HIGH | MED | INFO
    transaction_id  UUID          REFERENCES transactions(id),
    arn             VARCHAR(23),
    our_amount      NUMERIC(15,2),
    network_amount  NUMERIC(15,2),
    bank_amount     NUMERIC(15,2),
    notes           TEXT,
    resolved        BOOLEAN       NOT NULL DEFAULT FALSE,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recon_exc_run_resolved   ON reconciliation_exceptions(run_id, resolved);
CREATE INDEX idx_recon_exc_open_severity  ON reconciliation_exceptions(severity) WHERE resolved = FALSE;
