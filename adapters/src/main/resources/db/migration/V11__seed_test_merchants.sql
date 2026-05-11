-- V11: Seed test merchant and terminal for local development

INSERT INTO merchants (id, name, mcc, status, webhook_url, webhook_secret, mdr_percentage)
VALUES (
    'MERCH0000999',
    'Test Merchant Pvt Ltd',
    '5411',
    'ACTIVE',
    'http://merchant-simulator:9000/webhooks',
    'test-webhook-secret-key',
    0.0150
) ON CONFLICT (id) DO NOTHING;

INSERT INTO terminals (id, merchant_id, status)
VALUES ('TERM0042', 'MERCH0000999', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO merchant_reserve_accounts (merchant_id, balance, updated_at)
VALUES ('MERCH0000999', 0.00, NOW())
ON CONFLICT (merchant_id) DO NOTHING;
