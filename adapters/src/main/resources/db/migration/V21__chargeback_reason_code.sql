-- V21: Chargeback reason description and evidence deadline (N83)
-- LEARN: Visa reason codes — "10.4" is Fraud (Card Absent), "13.1" is Merchandise Not Received.
--        The 2-digit or dotted format is Visa; Mastercard uses 4-digit codes (e.g. 4853).
--        evidenceDeadline is 20 calendar days from chargeback date for Visa; 45 for Mastercard.
ALTER TABLE chargebacks ADD COLUMN reason_description VARCHAR(200);
ALTER TABLE chargebacks ADD COLUMN evidence_deadline DATE;
