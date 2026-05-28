-- V23: Authorization hold expiry (N81)
-- LEARN: Authorization hold — merchants pre-authorize an amount that is captured later (hotel check-in,
--        car rental). Visa/Mastercard rules allow up to 7 days for most MCCs; hotels (7011) and car
--        rentals (7512) can hold for 31 days. Expired holds must be reversed to release cardholder funds.
ALTER TABLE transactions ADD COLUMN authorization_expiry TIMESTAMPTZ;

CREATE INDEX idx_transactions_auth_expiry
    ON transactions(authorization_expiry)
    WHERE status = 'AUTHORIZED' AND authorization_expiry IS NOT NULL;
