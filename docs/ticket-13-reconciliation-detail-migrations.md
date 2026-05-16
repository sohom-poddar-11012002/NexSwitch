# Ticket #13 — Flyway V13–V15: Reconciliation Detail, Merchant Disputes, Payout Instructions

## What

Three Flyway migrations adding the remaining persistence layer tables needed by the reconciliation service, ops dispute flow, and payout job:

- **V13** — Four reconciliation detail tables: `reconciliation_network_records`, `reconciliation_bank_records`, `merchant_settlement_statements`, `reconciliation_exceptions`
- **V14** — `merchant_disputes` table (merchant raises discrepancy against our settlement statement)
- **V15** — `payout_instructions` table (payout job writes one row per merchant per cycle; UTR confirmed later)

## Why

Closes #13. These are the final schema tables required before the reconciliation service (§4.8 eight-step job) and payout job (§4.7 09:00 IST) can be implemented. `reconciliation_runs` already existed from V7; V13 adds the four detail tables it references.

## Design Decisions

- **Four tables in one migration (V13):** All four are reconciliation detail tables with tight FK dependencies on `reconciliation_runs`. Splitting them across migrations would make V7 unusable until V13 completes. One migration is the right unit.
- **`UNIQUE (merchant_id, statement_date)` on `merchant_settlement_statements`:** One statement per merchant per day — the reconciliation job is idempotent; re-running it overwrites via upsert semantics at the application layer.
- **`UNIQUE (merchant_id, payout_date)` on `payout_instructions`:** Same reasoning — payout job must be restartable; DB uniqueness prevents double-payout rows.
- **Partial indexes on open exceptions and pending payouts:** `WHERE resolved = FALSE` and `WHERE status IN ('PENDING', 'INITIATED')` keep these operational queries fast without indexing the full table history.
- **`resolution` nullable on `merchant_disputes`:** New disputes start with `resolution = NULL` (equivalent to PENDING) rather than a separate status column — fewer columns, same semantics. Index covers the `IS NULL` case.
- **`bank_reference_id` vs `utr_number` on `payout_instructions`:** Two separate fields because the bank reference is set at submission time (synchronous) while the UTR arrives asynchronously hours later via bank webhook or SFTP file.

## Test Coverage

No adapter tests in this ticket — migrations are SQL-only. Flyway validates structural correctness on startup. Integration tests for the reconciliation service (ticket #14+) will exercise these tables against a real Testcontainers Postgres instance.

## How to Verify

```bash
# Start Postgres via Docker Compose
docker compose up -d postgres

# Trigger Flyway migration (acquiring-service runs Flyway on startup)
docker compose up acquiring-service

# Confirm all 15 migrations applied
docker compose exec postgres psql -U nexswitch_app -d nexswitch \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

# Confirm new tables exist
docker compose exec postgres psql -U nexswitch_app -d nexswitch \
  -c "\dt reconciliation_*; \dt merchant_disputes; \dt payout_instructions;"
```
