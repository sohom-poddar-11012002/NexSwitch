-- V8: Merchant reserve accounts

CREATE TABLE merchant_reserve_accounts (
    merchant_id     VARCHAR(50)     PRIMARY KEY,
    balance         NUMERIC(15,2)   NOT NULL DEFAULT 0,
    currency        VARCHAR(3)         NOT NULL DEFAULT 'INR',
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE reserve_transactions (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     VARCHAR(50)     NOT NULL,
    transaction_id  UUID,
    chargeback_id   UUID,
    type            VARCHAR(30)     NOT NULL,   -- WITHHOLD | RELEASE | CHARGEBACK_DEBIT | CHARGEBACK_CREDIT
    amount          NUMERIC(15,2)   NOT NULL,
    balance_after   NUMERIC(15,2)   NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
