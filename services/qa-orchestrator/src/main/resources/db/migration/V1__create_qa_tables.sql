-- QA Orchestrator execution history
-- Flyway uses flyway_schema_history_qa (separate from acquiring-service's history table)

CREATE TABLE qa_run_executions (
    id              UUID        PRIMARY KEY,
    run_id          VARCHAR(255) NOT NULL,
    status          VARCHAR(50)  NOT NULL,
    run_variables   JSONB        NOT NULL DEFAULT '{}',
    started_at      TIMESTAMPTZ  NOT NULL,
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_qa_run_executions_run_id  ON qa_run_executions(run_id);
CREATE INDEX idx_qa_run_executions_status  ON qa_run_executions(status);
CREATE INDEX idx_qa_run_executions_started ON qa_run_executions(started_at DESC);

CREATE TABLE qa_step_results (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id    UUID         NOT NULL REFERENCES qa_run_executions(id) ON DELETE CASCADE,
    scenario_id     VARCHAR(255) NOT NULL,
    step_id         VARCHAR(255) NOT NULL,
    step_index      INT          NOT NULL,
    status          VARCHAR(50)  NOT NULL,
    actual_value    TEXT,
    expected_value  TEXT,
    failure_reason  TEXT,
    captured_vars   JSONB        NOT NULL DEFAULT '{}',
    elapsed_ms      BIGINT,
    recorded_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qa_step_results_execution_id ON qa_step_results(execution_id);
CREATE INDEX idx_qa_step_results_scenario_id  ON qa_step_results(scenario_id);
CREATE INDEX idx_qa_step_results_status       ON qa_step_results(status);
