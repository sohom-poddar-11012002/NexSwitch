-- V20: Partial approval support (N80)
-- LEARN: Partial approval — response code "10" means the issuer approved a lesser amount than requested.
--        Common for prepaid cards nearing zero balance. Terminal must display the approved amount
--        so the cardholder can pay the remainder via another method.
ALTER TABLE transactions ADD COLUMN approved_amount NUMERIC(15,2);
ALTER TABLE transactions ADD COLUMN approved_currency VARCHAR(3);
