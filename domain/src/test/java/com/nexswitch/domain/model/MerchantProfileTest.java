package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.*;

class MerchantProfileTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final BigDecimal MDR     = new BigDecimal("0.0150");
    private static final BigDecimal RESERVE = new BigDecimal("0.0500");

    private MerchantProfile sample() {
        return new MerchantProfile(
            MerchantId.of("MERCH0000999"),
            "Test Merchant",
            "5411",
            MerchantProfile.Status.ACTIVE,
            Money.of("500000.00", INR),
            Money.of("5000000.00", INR),
            MDR,
            RESERVE,
            "http://merchant.example.com/webhooks",
            "secret-key",
            "test@payswiff"
        );
    }

    @Test
    void createsMerchantProfile() {
        MerchantProfile profile = sample();
        assertThat(profile.merchantId().value()).isEqualTo("MERCH0000999");
        assertThat(profile.name()).isEqualTo("Test Merchant");
        assertThat(profile.mcc()).isEqualTo("5411");
        assertThat(profile.status()).isEqualTo(MerchantProfile.Status.ACTIVE);
        assertThat(profile.mdrPercentage()).isEqualByComparingTo(MDR);
        assertThat(profile.reservePercentage()).isEqualByComparingTo(RESERVE);
    }

    @Test
    void isActiveReturnsTrueForActiveStatus() {
        assertThat(sample().isActive()).isTrue();
    }

    @Test
    void isActiveReturnsFalseForSuspendedStatus() {
        var suspended = new MerchantProfile(
            MerchantId.of("MERCH0000999"), "Test", "5411",
            MerchantProfile.Status.SUSPENDED,
            Money.of("500000.00", INR), Money.of("5000000.00", INR),
            MDR, RESERVE, null, null, null
        );
        assertThat(suspended.isActive()).isFalse();
    }

    @Test
    void throwsWhenMerchantIdIsNull() {
        assertThatThrownBy(() -> new MerchantProfile(
            null, "Test", "5411", MerchantProfile.Status.ACTIVE,
            Money.of("500000.00", INR), Money.of("5000000.00", INR),
            MDR, RESERVE, null, null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenNameIsBlank() {
        assertThatThrownBy(() -> new MerchantProfile(
            MerchantId.of("MERCH0000999"), "  ", "5411", MerchantProfile.Status.ACTIVE,
            Money.of("500000.00", INR), Money.of("5000000.00", INR),
            MDR, RESERVE, null, null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenReservePercentageIsNull() {
        assertThatThrownBy(() -> new MerchantProfile(
            MerchantId.of("MERCH0000999"), "Test", "5411", MerchantProfile.Status.ACTIVE,
            Money.of("500000.00", INR), Money.of("5000000.00", INR),
            MDR, null, null, null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusEnumHasExpectedValues() {
        assertThat(MerchantProfile.Status.values()).containsExactlyInAnyOrder(
            MerchantProfile.Status.ACTIVE,
            MerchantProfile.Status.SUSPENDED,
            MerchantProfile.Status.TERMINATED
        );
    }
}
