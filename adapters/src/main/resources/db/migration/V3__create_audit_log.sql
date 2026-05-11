-- V3: Audit log — append-only enforced at DB level
-- REVOKE UPDATE, DELETE on audit_log revoked from app user

CREATE TABLE audit_log (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100)    NOT NULL,
    actor_service   VARCHAR(100)    NOT NULL,
    transaction_id  UUID,
    aggregate_id    VARCHAR(100),
    aggregate_type  VARCHAR(50),
    previous_state  VARCHAR(50),
    new_state       VARCHAR(50),
    event_data      JSONB           NOT NULL,
    recorded_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_transaction
    ON audit_log(transaction_id)
    WHERE transaction_id IS NOT NULL;

CREATE INDEX idx_audit_log_recorded_at
    ON audit_log(recorded_at DESC);

-- Append-only enforcement: application user cannot UPDATE or DELETE audit records
-- Note: revoke requires the user to exist; in local dev this may be a no-op
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'payments_app') THEN
        REVOKE UPDATE, DELETE ON audit_log FROM payments_app;
    END IF;
END $$;
