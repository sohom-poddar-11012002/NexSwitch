package com.payments.test.fixture;

import com.payments.domain.model.BinInfo;
import com.payments.domain.model.PaymentNetwork;

/**
 * Shared BinInfo fixtures aligned with V10 + V12 seed data.
 * BIN prefixes match rows already in bin_table — safe for DB-backed tests.
 */
public final class BinInfoFixture {

    private BinInfoFixture() {}

    /** Visa debit (HDFC) — standard gateway routing, not NFS-eligible. */
    public static BinInfo visaDebit() {
        return new BinInfo(
            "453914",
            PaymentNetwork.VISA,
            "HDFC Bank",
            "DEBIT",
            "VISA PLATINUM",
            "IN",
            "HDFC",
            false
        );
    }

    /** Mastercard credit — no issuer bank set (gateway-only routing). */
    public static BinInfo mastercardCredit() {
        return new BinInfo(
            "512345",
            PaymentNetwork.MASTERCARD,
            "ICICI Bank",
            "CREDIT",
            "MASTERCARD WORLD",
            "IN",
            null,
            false
        );
    }

    /** RuPay debit (SBI) — NFS-eligible, routes via NPCI NFS for on-us transactions. */
    public static BinInfo rupayNfs() {
        return new BinInfo(
            "606011",
            PaymentNetwork.RUPAY,
            "State Bank of India",
            "DEBIT",
            "RUPAY CLASSIC",
            "IN",
            "SBI",
            true
        );
    }

    /** RuPay debit (Canara) — NFS-eligible, different issuer for routing variation tests. */
    public static BinInfo rupayNfsCanara() {
        return new BinInfo(
            "607080",
            PaymentNetwork.RUPAY,
            "Canara Bank",
            "DEBIT",
            "RUPAY CLASSIC",
            "IN",
            "CANARA",
            true
        );
    }
}
