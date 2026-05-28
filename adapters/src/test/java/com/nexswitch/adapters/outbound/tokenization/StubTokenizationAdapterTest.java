package com.nexswitch.adapters.outbound.tokenization;

import com.nexswitch.domain.model.NetworkToken;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.PanHash;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class StubTokenizationAdapterTest {

    private final StubTokenizationAdapter adapter = new StubTokenizationAdapter();
    private static final PanHash PAN = PanHash.of("a".repeat(64));
    private static final MerchantId MERCH = MerchantId.of("MERCH0001");

    @Test
    void tokenize_returnsActiveToken_withExpectedFields() {
        NetworkToken token = adapter.tokenize(PAN, PaymentNetwork.VISA, MERCH);

        assertThat(token).isNotNull();
        assertThat(token.id()).isNotNull();
        assertThat(token.merchantId()).isEqualTo(MERCH);
        assertThat(token.network()).isEqualTo(PaymentNetwork.VISA);
        assertThat(token.status()).isEqualTo(NetworkToken.TokenStatus.ACTIVE);
        assertThat(token.networkTokenValue()).startsWith("STUB-TOKEN-");
    }

    @Test
    void tokenize_returnsDifferentTokensOnEachCall() {
        NetworkToken t1 = adapter.tokenize(PAN, PaymentNetwork.VISA, MERCH);
        NetworkToken t2 = adapter.tokenize(PAN, PaymentNetwork.VISA, MERCH);

        // Stub generates a random token each time
        assertThat(t1.networkTokenValue()).isNotEqualTo(t2.networkTokenValue());
    }

    @Test
    void lookup_returnsEmpty() {
        Optional<NetworkToken> result = adapter.lookup("any-token-value");

        assertThat(result).isEmpty();
    }

    @Test
    void suspend_doesNotThrow() {
        // Stub is a no-op — just verify it completes without exception
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> adapter.suspend("any-token-value"));
    }
}
