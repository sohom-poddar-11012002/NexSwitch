-- V17: Data integrity constraints — CHECK constraints + missing index
-- All constraints use NOT VALID / VALIDATE pattern so they run without a full table lock.
-- LEARN: NOT VALID adds the constraint without scanning existing rows (instant on large tables).
--        VALIDATE CONSTRAINT then scans rows with a ShareUpdateExclusiveLock — concurrent reads
--        and writes proceed; only another ALTER that would conflict is blocked. This is the
--        production-safe way to add CHECK constraints without downtime.

-- N32: positive_amount — zero and negative transaction amounts must never reach the DB
ALTER TABLE transactions
    ADD CONSTRAINT positive_amount CHECK (amount > 0) NOT VALID;
ALTER TABLE transactions
    VALIDATE CONSTRAINT positive_amount;

-- N33: non_negative_balance — reserve account balance cannot go negative
ALTER TABLE reserve_accounts
    ADD CONSTRAINT non_negative_balance CHECK (balance >= 0) NOT VALID;
ALTER TABLE reserve_accounts
    VALIDATE CONSTRAINT non_negative_balance;

-- N34 / G2-3: index on reconciliation_runs(status) — settlement batch queries filter
--             by status; without this the full table is scanned on every status check.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_recon_status
    ON reconciliation_runs(status);
