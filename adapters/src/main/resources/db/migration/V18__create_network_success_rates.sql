-- V18: network_success_rates — routing signal table for smart BIN/MCC/hour routing
-- LEARN: Materialized routing signal — instead of routing to the cheapest network blindly,
--        the routing engine reads p95 latency and success rates per (network, BIN, MCC, hour).
--        A network that is degraded for a specific BIN in the past hour gets de-prioritised
--        automatically — no ops intervention needed. Updated by the metrics pipeline.

CREATE TABLE network_success_rates (
    network          VARCHAR(20)    NOT NULL,
    issuer_bin_prefix VARCHAR(8)   NOT NULL,
    mcc              VARCHAR(4),
    hour_of_day      SMALLINT       NOT NULL,
    day_of_week      SMALLINT       NOT NULL,
    attempt_count    INT            NOT NULL DEFAULT 0,
    success_count    INT            NOT NULL DEFAULT 0,
    success_rate     NUMERIC(5,4),
    p95_latency_ms   INT,
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (network, issuer_bin_prefix, hour_of_day, day_of_week)
);

CREATE INDEX idx_net_rate_bin ON network_success_rates(issuer_bin_prefix, hour_of_day);
