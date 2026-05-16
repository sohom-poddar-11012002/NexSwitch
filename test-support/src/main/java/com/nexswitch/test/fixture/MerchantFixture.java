package com.nexswitch.test.fixture;

import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Shared MerchantProfile fixtures aligned with V11 seed data.
 * Use active() in tests that hit the real DB — the row already exists.
 */
// LEARN: TestFixture — active() mirrors V11 seed row; tests read from Postgres seeded by Flyway, not by fixture
public final class MerchantFixture {

    private static final Currency INR = Currency.getInstance("INR");

    private MerchantFixture() {}

    /** Active merchant matching V11 seed row — safe to use in DB-backed integration tests. */
    public static MerchantProfile active() {
        return new MerchantProfile(
            MerchantId.of("MERCH0000999"),
            "Test Merchant Pvt Ltd",
            "5411",
            MerchantProfile.Status.ACTIVE,
            Money.of("100000.00", INR),
            Money.of("1000000.00", INR),
            new BigDecimal("0.0150"),
            new BigDecimal("0.0500"),
            "http://merchant-simulator:9000/webhooks",
            "test-webhook-secret-key"
        );
    }

    /** Suspended merchant — useful for testing blocked-merchant rejection paths. */
    public static MerchantProfile suspended() {
        return new MerchantProfile(
            MerchantId.of("MERCH0000998"),
            "Suspended Merchant Ltd",
            "5411",
            MerchantProfile.Status.SUSPENDED,
            Money.of("100000.00", INR),
            Money.of("1000000.00", INR),
            new BigDecimal("0.0150"),
            new BigDecimal("0.0500"),
            null,
            null
        );
    }

    /** High-MDR merchant — useful for fee waterfall tests. */
    public static MerchantProfile highMdr() {
        return new MerchantProfile(
            MerchantId.of("MERCH0000997"),
            "High MDR Merchant Ltd",
            "7011",
            MerchantProfile.Status.ACTIVE,
            Money.of("500000.00", INR),
            Money.of("5000000.00", INR),
            new BigDecimal("0.0250"),
            new BigDecimal("0.0500"),
            "http://merchant-simulator:9000/webhooks",
            "test-webhook-secret-key"
        );
    }
}
