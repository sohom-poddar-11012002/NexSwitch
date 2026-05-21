package com.nexswitch.domain.service;

import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.StaticQRResult;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.port.inbound.GenerateStaticQRCommand;
import com.nexswitch.domain.port.outbound.MerchantRepository;
import com.nexswitch.domain.port.outbound.QrImageGeneratorPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GenerateStaticQRServiceTest {

    @Mock MerchantRepository   merchantRepository;
    @Mock QrImageGeneratorPort qrImageGeneratorPort;

    @InjectMocks GenerateStaticQRService service;

    private static final MerchantId MERCH_ID = new MerchantId("MERCH0000999");
    private static final Currency   INR      = Currency.getInstance("INR");

    @Test
    void unknownMerchant_returnsFailed() {
        when(merchantRepository.findById(MERCH_ID)).thenReturn(Optional.empty());

        StaticQRResult result = service.execute(new GenerateStaticQRCommand(MERCH_ID));

        assertThat(result).isInstanceOf(StaticQRResult.Failed.class);
        assertThat(((StaticQRResult.Failed) result).reason()).contains("MERCH0000999");
    }

    @Test
    void suspendedMerchant_returnsFailed() {
        when(merchantRepository.findById(MERCH_ID)).thenReturn(Optional.of(suspendedMerchant()));

        StaticQRResult result = service.execute(new GenerateStaticQRCommand(MERCH_ID));

        assertThat(result).isInstanceOf(StaticQRResult.Failed.class);
    }

    @Test
    void activeMerchant_returnsGenerated() {
        when(merchantRepository.findById(MERCH_ID)).thenReturn(Optional.of(activeMerchant()));
        when(qrImageGeneratorPort.generateBase64Png(anyString(), anyInt(), anyInt())).thenReturn("base64png==");

        StaticQRResult result = service.execute(new GenerateStaticQRCommand(MERCH_ID));

        assertThat(result).isInstanceOf(StaticQRResult.Generated.class);
        StaticQRResult.Generated g = (StaticQRResult.Generated) result;
        assertThat(g.qrImageBase64()).isEqualTo("base64png==");
        assertThat(g.vpa()).isEqualTo("merch0000999@payswiff");
    }

    @Test
    void upiString_containsVpaAndNameAndMcc_butNoAmount() {
        when(merchantRepository.findById(MERCH_ID)).thenReturn(Optional.of(activeMerchant()));
        when(qrImageGeneratorPort.generateBase64Png(anyString(), anyInt(), anyInt())).thenReturn("img");

        StaticQRResult result = service.execute(new GenerateStaticQRCommand(MERCH_ID));

        StaticQRResult.Generated g = (StaticQRResult.Generated) result;
        assertThat(g.upiString()).contains("pa=merch0000999%40payswiff");
        assertThat(g.upiString()).contains("pn=");
        assertThat(g.upiString()).contains("mc=5411");
        assertThat(g.upiString()).doesNotContain("am=");
    }

    @Test
    void fallbackVpa_usedWhenMerchantVpaIsNull() {
        when(merchantRepository.findById(MERCH_ID)).thenReturn(Optional.of(merchantWithNullVpa()));
        when(qrImageGeneratorPort.generateBase64Png(anyString(), anyInt(), anyInt())).thenReturn("img");

        StaticQRResult result = service.execute(new GenerateStaticQRCommand(MERCH_ID));

        StaticQRResult.Generated g = (StaticQRResult.Generated) result;
        assertThat(g.vpa()).isEqualTo("MERCH0000999@payswiff");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MerchantProfile activeMerchant() {
        return new MerchantProfile(
                MERCH_ID, "Test Merchant", "5411", MerchantProfile.Status.ACTIVE,
                Money.of(new BigDecimal("50000.00"), INR),
                Money.of(new BigDecimal("200000.00"), INR),
                new BigDecimal("1.80"), new BigDecimal("2.00"),
                "https://webhook.test", "secret", "merch0000999@payswiff");
    }

    private MerchantProfile suspendedMerchant() {
        return new MerchantProfile(
                MERCH_ID, "Suspended", "5411", MerchantProfile.Status.SUSPENDED,
                Money.of(new BigDecimal("50000.00"), INR),
                Money.of(new BigDecimal("200000.00"), INR),
                new BigDecimal("1.80"), new BigDecimal("2.00"),
                null, null, null);
    }

    private MerchantProfile merchantWithNullVpa() {
        return new MerchantProfile(
                MERCH_ID, "No VPA Merchant", "5411", MerchantProfile.Status.ACTIVE,
                Money.of(new BigDecimal("50000.00"), INR),
                Money.of(new BigDecimal("200000.00"), INR),
                new BigDecimal("1.80"), new BigDecimal("2.00"),
                "https://webhook.test", "secret", null);
    }
}
