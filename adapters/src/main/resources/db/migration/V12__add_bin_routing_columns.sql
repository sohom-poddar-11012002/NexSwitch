-- BinInfo now carries issuerBank (for on-us detection) and nfsEligible (for IBL routing).
-- These were added to the domain model in PR #29 but the bin_table schema lagged behind.

ALTER TABLE bin_table
    ADD COLUMN IF NOT EXISTS issuer_bank_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS nfs_eligible     BOOLEAN NOT NULL DEFAULT FALSE;

-- Seed the existing BIN rows with known values where applicable.
-- Rows without an explicit issuer_bank_code remain NULL (treated as gateway-only routing).
UPDATE bin_table SET issuer_bank_code = 'HDFC',   nfs_eligible = FALSE WHERE bin_prefix LIKE '453914%';
UPDATE bin_table SET issuer_bank_code = 'SBI',    nfs_eligible = TRUE  WHERE bin_prefix LIKE '601200%';
UPDATE bin_table SET issuer_bank_code = 'CANARA', nfs_eligible = TRUE  WHERE bin_prefix LIKE '607080%';
UPDATE bin_table SET issuer_bank_code = 'SBI',    nfs_eligible = TRUE  WHERE bin_prefix LIKE '606011%';
