package com.nexswitch.domain.service;

import com.nexswitch.domain.model.CollectRequest;
import com.nexswitch.domain.model.InitiateCollectResult;
import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.port.inbound.InitiateCollectCommand;
import com.nexswitch.domain.port.outbound.CollectRequestPort;
import com.nexswitch.domain.port.outbound.MerchantRepository;
import com.nexswitch.domain.port.outbound.UpiPspNotifier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class InitiateCollectServiceTest {

    @Mock MerchantRepository  merchantRepository;
    @Mock CollectRequestPort  collectRequestPort;
    @Mock UpiPspNotifier      upiPspNotifier;

    @InjectMocks InitiateCollectService service;

    private static final MerchantId MERCH   = new MerchantId("MERCH0000999");
    private static final Currency   INR     = Currency.getInstance("INR");
    private static final Money      AMOUNT  = Money.of(new BigDecimal("500.00"), INR);

    @Test
    void unknownMerchant_returnsFailed() {
        when(merchantRepository.findById(MERCH)).thenReturn(Optional.empty());

        InitiateCollectResult result = service.execute(command());

        assertThat(result).isInstanceOf(InitiateCollectResult.Failed.class);
        verifyNoInteractions(collectRequestPort, upiPspNotifier);
    }

    @Test
    void suspendedMerchant_returnsFailed() {
        when(merchantRepository.findById(MERCH)).thenReturn(Optional.of(suspended()));

        InitiateCollectResult result = service.execute(command());

        assertThat(result).isInstanceOf(InitiateCollectResult.Failed.class);
        verifyNoInteractions(collectRequestPort, upiPspNotifier);
    }

    @Test
    void validCollect_returnsInitiated() {
        when(merchantRepository.findById(MERCH)).thenReturn(Optional.of(active()));

        InitiateCollectResult result = service.execute(command());

        assertThat(result).isInstanceOf(InitiateCollectResult.Initiated.class);
        InitiateCollectResult.Initiated i = (InitiateCollectResult.Initiated) result;
        assertThat(i.collectId().value()).startsWith("COL");
        assertThat(i.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void validCollect_savesAndNotifiesPsp() {
        when(merchantRepository.findById(MERCH)).thenReturn(Optional.of(active()));

        service.execute(command());

        verify(collectRequestPort).save(any(CollectRequest.class));
        verify(upiPspNotifier).sendCollectRequest(any(CollectRequest.class));
    }

    @Test
    void collectRequest_hasPendingStatusAndCorrectExpiry() {
        when(merchantRepository.findById(MERCH)).thenReturn(Optional.of(active()));
        ArgumentCaptor<CollectRequest> captor = ArgumentCaptor.forClass(CollectRequest.class);

        service.execute(command());

        verify(collectRequestPort).save(captor.capture());
        CollectRequest saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(CollectRequest.Status.PENDING);
        assertThat(saved.payerVpa()).isEqualTo("customer@upi");
        assertThat(saved.amount().amount()).isEqualByComparingTo("500.00");
        assertThat(saved.expiresAt().getEpochSecond() - saved.createdAt().getEpochSecond())
                .isEqualTo(180L);
    }

    @Test
    void collectId_isUnique() {
        when(merchantRepository.findById(MERCH)).thenReturn(Optional.of(active()));

        InitiateCollectResult r1 = service.execute(command());
        InitiateCollectResult r2 = service.execute(command());

        String id1 = ((InitiateCollectResult.Initiated) r1).collectId().value();
        String id2 = ((InitiateCollectResult.Initiated) r2).collectId().value();
        assertThat(id1).isNotEqualTo(id2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InitiateCollectCommand command() {
        return new InitiateCollectCommand(MERCH, "customer@upi", AMOUNT, "order-001", 180);
    }

    private MerchantProfile active() {
        return new MerchantProfile(MERCH, "Merchant", "5411", MerchantProfile.Status.ACTIVE,
                Money.of(new BigDecimal("50000.00"), INR), Money.of(new BigDecimal("200000.00"), INR),
                new BigDecimal("1.80"), new BigDecimal("2.00"),
                "https://wh.test", "secret", "merch0000999@payswiff");
    }

    private MerchantProfile suspended() {
        return new MerchantProfile(MERCH, "Suspended", "5411", MerchantProfile.Status.SUSPENDED,
                Money.of(new BigDecimal("50000.00"), INR), Money.of(new BigDecimal("200000.00"), INR),
                new BigDecimal("1.80"), new BigDecimal("2.00"), null, null, null);
    }
}
