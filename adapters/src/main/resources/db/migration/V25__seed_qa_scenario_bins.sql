-- V25: Seed BIN prefixes used by QA scenarios
-- All 51 QA scenarios use these specific PANs; without these BINs
-- AuthorizationService returns RC=57 (BIN_NOT_FOUND) before reaching upstream.

INSERT INTO bin_table (bin_prefix, network, issuer_name, card_type, card_product) VALUES
    -- VISA 453914 — used by visa-auth-approved and ~25 other scenarios
    ('453914', 'VISA',       'QA Test Issuing Bank', 'CREDIT', 'Visa Classic'),
    -- Mastercard 520082 — used by mastercard-auth-approved
    ('520082', 'MASTERCARD', 'QA Test Issuing Bank', 'CREDIT', 'Mastercard Standard'),
    -- RuPay 607482 — used by rupay-auth-approved
    ('607482', 'RUPAY',      'QA Test Issuing Bank', 'DEBIT',  'RuPay Classic'),
    -- Amex 378282 — used by amex-auth-approved
    ('378282', 'AMEX',       'QA Test Issuing Bank', 'CREDIT', 'Amex Green'),
    -- Diners 305693 — used by diners-auth-approved
    ('305693', 'DINERS',     'QA Test Issuing Bank', 'CREDIT', 'Diners Club International')
ON CONFLICT (bin_prefix) DO NOTHING;
