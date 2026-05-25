CREATE TABLE collect_requests (
    collect_id    VARCHAR(19)    NOT NULL PRIMARY KEY,
    merchant_id   VARCHAR(16)    NOT NULL,
    payer_vpa     VARCHAR(100)   NOT NULL,
    amount        NUMERIC(19, 2) NOT NULL,
    currency      CHAR(3)        NOT NULL DEFAULT 'INR',
    order_id      VARCHAR(64)    NOT NULL,
    status        VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
    npci_txn_id   VARCHAR(64),
    created_at    TIMESTAMPTZ    NOT NULL,
    expires_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_collect_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)
);

CREATE INDEX idx_collect_merchant ON collect_requests(merchant_id);
CREATE INDEX idx_collect_status   ON collect_requests(status);
CREATE INDEX idx_collect_expires  ON collect_requests(expires_at);
