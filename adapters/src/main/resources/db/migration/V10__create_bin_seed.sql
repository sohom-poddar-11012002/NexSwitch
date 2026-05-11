-- V10: Seed BIN table with common test BIN ranges

INSERT INTO bin_table (bin_prefix, network, issuer_name, card_type, card_product) VALUES
    ('453936', 'VISA',       'Test Issuing Bank', 'CREDIT', 'Visa Classic'),
    ('453975', 'VISA',       'Test Issuing Bank', 'DEBIT',  'Visa Debit'),
    ('522164', 'MASTERCARD', 'Test Issuing Bank', 'CREDIT', 'Mastercard Standard'),
    ('510510', 'MASTERCARD', 'Test Issuing Bank', 'DEBIT',  'Mastercard Debit'),
    ('607388', 'RUPAY',      'Test Issuing Bank', 'DEBIT',  'RuPay Classic'),
    ('508528', 'RUPAY',      'Test Issuing Bank', 'CREDIT', 'RuPay Credit')
ON CONFLICT (bin_prefix) DO NOTHING;
