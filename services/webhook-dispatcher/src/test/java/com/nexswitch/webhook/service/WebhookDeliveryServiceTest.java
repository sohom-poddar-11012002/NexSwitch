package com.nexswitch.webhook.service;

import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.port.outbound.MerchantRepository;
import com.nexswitch.webhook.persistence.JpaWebhookDeliveryRepository;
import com.nexswitch.webhook.persistence.WebhookDeliveryEntity;
import com.nexswitch.webhook.persistence.WebhookDeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

// LEARN: TestDouble — RestClient is stubbed via a fake HttpSender functional interface so we
//        can simulate 200, 500, and connection-refused without a real HTTP server.
@ExtendWith(MockitoExtension.class)
class WebhookDeliveryServiceTest {

    @Mock MerchantRepository                 merchantRepository;
    @Mock JpaWebhookDeliveryRepository       deliveryRepository;
    @Mock WebhookDeliveryService.HttpSender  httpSender;
    @Captor ArgumentCaptor<Map<String, String>> headersCaptor;

    WebhookDeliveryService service;

    static final String MERCHANT_ID  = "MERCH0000999";
    static final String WEBHOOK_URL  = "https://merchant.example.com/webhook";
    static final String WEBHOOK_SECRET = "super-secret-key";

    @BeforeEach
    void setUp() {
        service = new WebhookDeliveryService(merchantRepository, deliveryRepository, httpSender, 5);
        lenient().when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy path: 200 on first attempt ─────────────────────────────────────

    @Test
    void givenMerchantWithWebhook_whenDeliverySucceeds_thenStatusDelivered() {
        when(merchantRepository.findById(MerchantId.of(MERCHANT_ID)))
                .thenReturn(Optional.of(activeMerchantWithWebhook()));
        when(httpSender.post(eq(WEBHOOK_URL), any(), any())).thenReturn(200);

        service.dispatch(MERCHANT_ID, "transaction.authorized", "{\"id\":\"abc\"}");

        ArgumentCaptor<WebhookDeliveryEntity> captor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(deliveryRepository, atLeastOnce()).save(captor.capture());
        WebhookDeliveryEntity last = captor.getAllValues().getLast();
        assertThat(last.status()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(last.attemptCount()).isEqualTo(1);
    }

    // ── Retry: 500 twice then 200 ─────────────────────────────────────────────

    @Test
    void givenTransientFailure_whenRetrySucceeds_thenStatusDelivered() {
        when(merchantRepository.findById(MerchantId.of(MERCHANT_ID)))
                .thenReturn(Optional.of(activeMerchantWithWebhook()));
        when(httpSender.post(eq(WEBHOOK_URL), any(), any()))
                .thenReturn(500)
                .thenReturn(500)
                .thenReturn(200);

        service.dispatch(MERCHANT_ID, "transaction.authorized", "{\"id\":\"abc\"}");

        ArgumentCaptor<WebhookDeliveryEntity> captor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(deliveryRepository, atLeastOnce()).save(captor.capture());
        WebhookDeliveryEntity last = captor.getAllValues().getLast();
        assertThat(last.status()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(last.attemptCount()).isEqualTo(3);
    }

    // ── DLQ: all 5 attempts fail ──────────────────────────────────────────────

    @Test
    void givenAllAttemptsExhausted_thenStatusFailedAndDlqPublished() {
        when(merchantRepository.findById(MerchantId.of(MERCHANT_ID)))
                .thenReturn(Optional.of(activeMerchantWithWebhook()));
        when(httpSender.post(eq(WEBHOOK_URL), any(), any())).thenReturn(500);

        WebhookDeliveryService.DlqPublisher dlqPublisher = mock(WebhookDeliveryService.DlqPublisher.class);
        service = new WebhookDeliveryService(merchantRepository, deliveryRepository, httpSender, 5, dlqPublisher);
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.dispatch(MERCHANT_ID, "transaction.authorized", "{\"id\":\"abc\"}");

        ArgumentCaptor<WebhookDeliveryEntity> captor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(deliveryRepository, atLeastOnce()).save(captor.capture());
        WebhookDeliveryEntity last = captor.getAllValues().getLast();
        assertThat(last.status()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(last.attemptCount()).isEqualTo(5);
        verify(dlqPublisher).publish(any());
    }

    // ── No webhook configured — skip silently ────────────────────────────────

    @Test
    void givenMerchantWithNoWebhookUrl_thenNoHttpCallMade() {
        when(merchantRepository.findById(MerchantId.of(MERCHANT_ID)))
                .thenReturn(Optional.of(merchantWithoutWebhook()));

        service.dispatch(MERCHANT_ID, "transaction.authorized", "{\"id\":\"abc\"}");

        verify(httpSender, never()).post(any(), any(), any());
        verify(deliveryRepository, never()).save(any());
    }

    // ── Merchant not found — skip silently ───────────────────────────────────

    @Test
    void givenMerchantNotFound_thenNoHttpCallMade() {
        when(merchantRepository.findById(any())).thenReturn(Optional.empty());

        service.dispatch(MERCHANT_ID, "transaction.authorized", "{\"id\":\"abc\"}");

        verify(httpSender, never()).post(any(), any(), any());
    }

    // ── Idempotency key header sent ───────────────────────────────────────────

    @Test
    void givenSuccessfulDelivery_thenIdempotencyKeyHeaderIncluded() {
        when(merchantRepository.findById(MerchantId.of(MERCHANT_ID)))
                .thenReturn(Optional.of(activeMerchantWithWebhook()));
        when(httpSender.post(eq(WEBHOOK_URL), any(), any())).thenReturn(200);

        service.dispatch(MERCHANT_ID, "transaction.authorized", "{\"id\":\"abc\"}");

        verify(httpSender).post(eq(WEBHOOK_URL), any(), headersCaptor.capture());
        assertThat(headersCaptor.getValue()).containsKey("X-Idempotency-Key");
        assertThat(headersCaptor.getValue()).containsKey("X-NexSwitch-Signature");
        assertThat(headersCaptor.getValue().get("X-NexSwitch-Signature")).startsWith("sha256=");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MerchantProfile activeMerchantWithWebhook() {
        return new MerchantProfile(
                MerchantId.of(MERCHANT_ID), "Test Merchant", "5411",
                MerchantProfile.Status.ACTIVE,
                Money.of("100000.00", Currency.getInstance("INR")),
                Money.of("1000000.00", Currency.getInstance("INR")),
                new BigDecimal("1.50"), new BigDecimal("5.00"),
                WEBHOOK_URL, WEBHOOK_SECRET, null
        );
    }

    private static MerchantProfile merchantWithoutWebhook() {
        return new MerchantProfile(
                MerchantId.of(MERCHANT_ID), "Test Merchant", "5411",
                MerchantProfile.Status.ACTIVE,
                Money.of("100000.00", Currency.getInstance("INR")),
                Money.of("1000000.00", Currency.getInstance("INR")),
                new BigDecimal("1.50"), new BigDecimal("5.00"),
                null, null, null
        );
    }
}
