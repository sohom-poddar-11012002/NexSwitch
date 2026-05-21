package com.nexswitch.domain.service;

import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.QRGenerationResult;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.model.vo.TerminalId;
import com.nexswitch.domain.port.inbound.QRGenerationCommand;
import com.nexswitch.domain.port.outbound.MerchantRepository;
import com.nexswitch.domain.port.outbound.QrImageGeneratorPort;
import com.nexswitch.domain.port.outbound.QrSessionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class GenerateQRServiceTest {

    private static final Currency INR       = Currency.getInstance("INR");
    private static final MerchantId MERCHANT = MerchantId.of("MERCH0000999");
    private static final TerminalId TERMINAL = new TerminalId("TERM0001");
    private static final Money AMOUNT       = Money.of(new BigDecimal("6000.00"), INR);

    @Mock private MerchantRepository merchantRepository;
    @Mock private QrSessionPort       qrSessionPort;
    @Mock private QrImageGeneratorPort imageGenerator;

    private GenerateQRService service;

    @BeforeEach
    void setUp() {
        service = new GenerateQRService(
                new QRSessionManager(5),
                qrSessionPort,
                imageGenerator,
                merchantRepository
        );
    }

    @Test
    void execute_returnGenerated_whenMerchantActive() {
        when(merchantRepository.findById(MERCHANT)).thenReturn(Optional.of(activeMerchant()));
        when(imageGenerator.generateBase64Png(anyString(), eq(300), eq(300)))
                .thenReturn("BASE64_PNG_DATA");

        QRGenerationResult result = service.execute(command("order-001"));

        assertThat(result).isInstanceOf(QRGenerationResult.Generated.class);
        QRGenerationResult.Generated generated = (QRGenerationResult.Generated) result;
        assertThat(generated.txnRef()).startsWith("TXN");
        assertThat(generated.qrImageBase64()).isEqualTo("BASE64_PNG_DATA");
        assertThat(generated.expiresAt()).isNotNull();
    }

    @Test
    void execute_savesSession() {
        when(merchantRepository.findById(MERCHANT)).thenReturn(Optional.of(activeMerchant()));
        when(imageGenerator.generateBase64Png(anyString(), anyInt(), anyInt())).thenReturn("img");

        service.execute(command("order-002"));

        verify(qrSessionPort).save(argThat(session ->
                session.merchantId().equals(MERCHANT) &&
                session.amount().equals(AMOUNT) &&
                session.status() == com.nexswitch.domain.model.QRSession.Status.PENDING
        ));
    }

    @Test
    void execute_generatesUpiStringContainingVpa() {
        when(merchantRepository.findById(MERCHANT)).thenReturn(Optional.of(activeMerchant()));
        when(imageGenerator.generateBase64Png(anyString(), anyInt(), anyInt())).thenReturn("img");

        service.execute(command("order-003"));

        verify(imageGenerator).generateBase64Png(
                argThat(upi -> upi.contains("pa=merch0000999%40payswiff")), eq(300), eq(300));
    }

    @Test
    void execute_usesFallbackVpa_whenMerchantVpaIsNull() {
        MerchantProfile noVpa = merchantWithNoVpa();
        when(merchantRepository.findById(MERCHANT)).thenReturn(Optional.of(noVpa));
        when(imageGenerator.generateBase64Png(anyString(), anyInt(), anyInt())).thenReturn("img");

        service.execute(command("order-004"));

        // Fallback VPA is merchantId@payswiff
        verify(imageGenerator).generateBase64Png(
                argThat(upi -> upi.contains("pa=MERCH0000999%40payswiff")), eq(300), eq(300));
    }

    @Test
    void execute_returnsFailed_whenMerchantNotFound() {
        when(merchantRepository.findById(MERCHANT)).thenReturn(Optional.empty());

        QRGenerationResult result = service.execute(command("order-005"));

        assertThat(result).isInstanceOf(QRGenerationResult.Failed.class);
        verify(qrSessionPort, never()).save(any());
        verify(imageGenerator, never()).generateBase64Png(any(), anyInt(), anyInt());
    }

    @Test
    void execute_returnsFailed_whenMerchantSuspended() {
        when(merchantRepository.findById(MERCHANT)).thenReturn(Optional.of(suspendedMerchant()));

        QRGenerationResult result = service.execute(command("order-006"));

        assertThat(result).isInstanceOf(QRGenerationResult.Failed.class);
        assertThat(((QRGenerationResult.Failed) result).reason()).contains("not active");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private QRGenerationCommand command(String orderId) {
        return new QRGenerationCommand(MERCHANT, TERMINAL, AMOUNT, orderId);
    }

    private MerchantProfile activeMerchant() {
        return new MerchantProfile(
                MERCHANT, "Test Merchant", "5411", MerchantProfile.Status.ACTIVE,
                Money.of("100000.00", INR), Money.of("1000000.00", INR),
                new BigDecimal("0.0150"), new BigDecimal("0.0500"),
                "http://webhook.example.com", "secret", "merch0000999@payswiff"
        );
    }

    private MerchantProfile merchantWithNoVpa() {
        return new MerchantProfile(
                MERCHANT, "Test Merchant", "5411", MerchantProfile.Status.ACTIVE,
                Money.of("100000.00", INR), Money.of("1000000.00", INR),
                new BigDecimal("0.0150"), new BigDecimal("0.0500"),
                null, null, null
        );
    }

    private MerchantProfile suspendedMerchant() {
        return new MerchantProfile(
                MERCHANT, "Test Merchant", "5411", MerchantProfile.Status.SUSPENDED,
                Money.of("100000.00", INR), Money.of("1000000.00", INR),
                new BigDecimal("0.0150"), new BigDecimal("0.0500"),
                null, null, null
        );
    }
}
