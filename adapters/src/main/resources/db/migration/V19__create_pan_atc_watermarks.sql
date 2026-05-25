-- V19: pan_atc_watermarks — ATC (Application Transaction Counter) replay detection
-- LEARN: EMV ATC — every chip transaction increments a monotonic counter inside the card.
--        Storing the highest ATC seen per PAN hash lets the issuer detect replayed
--        transactions: if ATC ≤ last_seen_atc the card was cloned or the message was
--        replayed. This is a core EMV security check (EMV Book 2, §8.1.1).

CREATE TABLE pan_atc_watermarks (
    pan_hash    VARCHAR(64)  NOT NULL PRIMARY KEY,
    last_seen_atc INT        NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Partial index: only look up recent watermarks (updated in the last 30 days)
-- to keep the index small for high-frequency read path.
CREATE INDEX idx_atc_updated ON pan_atc_watermarks(updated_at DESC)
    WHERE updated_at > NOW() - INTERVAL '30 days';
