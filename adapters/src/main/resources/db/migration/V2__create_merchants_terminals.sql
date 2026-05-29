-- V2: Merchants and terminals

CREATE TABLE merchants (
    id                  VARCHAR(15)     PRIMARY KEY,
    name                VARCHAR(100)    NOT NULL,
    mcc                 VARCHAR(4)         NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    webhook_url         VARCHAR(500),
    webhook_secret      VARCHAR(100),
    mdr_percentage      NUMERIC(5,4)    NOT NULL DEFAULT 0.0150,
    per_txn_limit       NUMERIC(15,2)   NOT NULL DEFAULT 500000.00,
    daily_limit         NUMERIC(15,2)   NOT NULL DEFAULT 5000000.00,
    reserve_percentage  NUMERIC(5,4)    NOT NULL DEFAULT 0.0500,
    vpa                 VARCHAR(100),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE terminals (
    id                  VARCHAR(8)      PRIMARY KEY,
    merchant_id         VARCHAR(15)     NOT NULL REFERENCES merchants(id),
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    terminal_key_id     VARCHAR(50),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_terminals_merchant
    ON terminals(merchant_id);

CREATE TABLE bin_table (
    bin_prefix          VARCHAR(8)      PRIMARY KEY,
    network             VARCHAR(20)     NOT NULL,
    issuer_name         VARCHAR(100),
    card_type           VARCHAR(20),
    card_product        VARCHAR(50),
    country_code        VARCHAR(2)         DEFAULT 'IN',
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
