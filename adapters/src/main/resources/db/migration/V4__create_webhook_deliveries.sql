-- V4: Webhook delivery tracking

CREATE TABLE webhook_deliveries (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        VARCHAR(100)    NOT NULL,
    merchant_id     VARCHAR(50)     NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    attempt_count   INT             DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    response_code   INT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_deliveries_status
    ON webhook_deliveries(status, created_at)
    WHERE status IN ('PENDING', 'FAILED');
