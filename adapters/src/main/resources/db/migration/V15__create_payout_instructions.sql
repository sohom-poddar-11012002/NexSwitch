-- V15: Payout instructions
-- Created by the payout job (09:00 IST) after reconciliation confirms settlement.
-- One row per merchant per payout cycle. UTR populated when bank confirms transfer.

CREATE TABLE payout_instructions (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id         VARCHAR(15)   NOT NULL REFERENCES merchants(id),
    payout_date         DATE          NOT NULL,
    gross_amount        NUMERIC(15,2) NOT NULL,
    reserve_withheld    NUMERIC(15,2) NOT NULL,
    net_amount          NUMERIC(15,2) NOT NULL,
    payment_mode        VARCHAR(10)   NOT NULL DEFAULT 'NEFT',  -- NEFT | RTGS | IMPS
    bank_reference_id   VARCHAR(100),   -- reference returned by partner bank on submission
    utr_number          VARCHAR(50),    -- Unique Transaction Reference from bank after credit
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING | INITIATED | COMPLETED | FAILED
    initiated_at        TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (merchant_id, payout_date)
);

CREATE INDEX idx_payout_instructions_status ON payout_instructions(status, payout_date DESC);
CREATE INDEX idx_payout_instructions_pending
    ON payout_instructions(payout_date)
    WHERE status IN ('PENDING', 'INITIATED');
