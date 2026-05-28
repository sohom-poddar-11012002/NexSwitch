package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.MerchantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class NetworkTokenTest {

    private NetworkToken valid() {
        return new NetworkToken(
                UUID.randomUUID(),
                MerchantId.of("MERCH0001"),
                "STUB-TOKEN-ABCD1234567890",
                "1234",
                PaymentNetwork.VISA,
                LocalDate.now().plusYears(4),
                NetworkToken.TokenStatus.ACTIVE
        );
    }

    @Test
    void valid_token_constructs_successfully() {
        assertThatNoException().isThrownBy(this::valid);
    }

    @Test
    void null_id_throws() {
        assertThatThrownBy(() -> new NetworkToken(
                null, MerchantId.of("MERCH0001"), "TOKEN", "1234",
                PaymentNetwork.VISA, LocalDate.now().plusYears(1), NetworkToken.TokenStatus.ACTIVE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalid_lastFour_throws() {
        assertThatThrownBy(() -> new NetworkToken(
                UUID.randomUUID(), MerchantId.of("MERCH0001"), "TOKEN", "ABC",
                PaymentNetwork.VISA, LocalDate.now().plusYears(1), NetworkToken.TokenStatus.ACTIVE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void token_status_enum_has_three_values() {
        assertThat(NetworkToken.TokenStatus.values()).containsExactlyInAnyOrder(
                NetworkToken.TokenStatus.ACTIVE,
                NetworkToken.TokenStatus.SUSPENDED,
                NetworkToken.TokenStatus.DELETED
        );
    }
}
