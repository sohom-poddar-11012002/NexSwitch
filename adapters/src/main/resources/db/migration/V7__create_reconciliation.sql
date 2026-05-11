-- V7: Reconciliation runs

CREATE TABLE reconciliation_runs (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    run_date                DATE        NOT NULL UNIQUE,
    total_transactions      INT,
    matched_count           INT,
    mismatch_count          INT,
    unknown_resolved_count  INT,
    summary_s3_key          VARCHAR(500),
    exceptions_s3_key       VARCHAR(500),
    status                  VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    started_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ
);
