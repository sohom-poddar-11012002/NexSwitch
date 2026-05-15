package com.payments.domain.model;

public sealed interface NetworkRoute
        permits NetworkRoute.OnUs, NetworkRoute.Ibl, NetworkRoute.Gateway {

    PaymentNetwork network();

    // Direct bank-to-bank path. No card network involved. Zero interchange, zero assessment.
    record OnUs(PaymentNetwork network, String bankCode) implements NetworkRoute {}

    // NPCI NFS (Interbank Link). Domestic debit + RuPay cards eligible per RBI mandate.
    record Ibl(PaymentNetwork network) implements NetworkRoute {}

    // Standard card network path: VisaNet (Visa), Banknet (MC), NPCI UPI (UPI).
    record Gateway(PaymentNetwork network) implements NetworkRoute {}
}
