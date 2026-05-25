package com.nexswitch.acquiring.rest;

import com.nexswitch.domain.model.CollectRequest;
import com.nexswitch.domain.model.InitiateCollectResult;
import com.nexswitch.domain.model.vo.CollectId;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.port.inbound.InitiateCollectUseCase;
import com.nexswitch.domain.port.outbound.CollectRequestPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UpiCollectControllerTest {

    @Mock InitiateCollectUseCase initiateCollectUseCase;
    @Mock CollectRequestPort     collectRequestPort;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new UpiCollectController(initiateCollectUseCase, collectRequestPort, ""))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validInitiate_returns200WithCollectId() throws Exception {
        when(initiateCollectUseCase.execute(any()))
                .thenReturn(new InitiateCollectResult.Initiated(new CollectId("COL1234567890ABCDEF"),
                        Instant.now().plusSeconds(180)));

        mvc.perform(post("/upi/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"merchantId":"MERCH0000999","payerVpa":"customer@upi",
                         "amount":"500.00","currency":"INR","orderId":"order-001"}
                        """))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.collectId").value("COL1234567890ABCDEF"));
    }

    @Test
    void unknownMerchant_returns400() throws Exception {
        when(initiateCollectUseCase.execute(any()))
                .thenReturn(new InitiateCollectResult.Failed("Merchant not found"));

        mvc.perform(post("/upi/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"merchantId":"UNKNOWN","payerVpa":"customer@upi",
                         "amount":"500.00","currency":"INR","orderId":"order-001"}
                        """))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Merchant not found"));
    }

    @Test
    void missingPayerVpa_returns400WithViolation() throws Exception {
        mvc.perform(post("/upi/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"merchantId":"MERCH0000999","amount":"500.00","currency":"INR","orderId":"order-001"}
                        """))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.violations[?(@.field=='payerVpa')]").exists());
    }

    @Test
    void outcome_approves_pendingCollect() throws Exception {
        CollectRequest pending = CollectRequest.builder()
                .collectId(new CollectId("COL1234567890ABCDEF"))
                .merchantId(new MerchantId("MERCH0000999"))
                .payerVpa("customer@upi")
                .amount(Money.of(new BigDecimal("500.00"), Currency.getInstance("INR")))
                .orderId("order-001")
                .status(CollectRequest.Status.PENDING)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(180))
                .build();

        when(collectRequestPort.findByCollectId("COL1234567890ABCDEF"))
                .thenReturn(Optional.of(pending));

        mvc.perform(post("/upi/collect/outcome")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"collectId":"COL1234567890ABCDEF","status":"APPROVED","npciTxnId":"NPCI999"}
                        """))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void outcome_notFound_returns400() throws Exception {
        when(collectRequestPort.findByCollectId("COLNOTEXIST")).thenReturn(Optional.empty());

        mvc.perform(post("/upi/collect/outcome")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"collectId":"COLNOTEXIST","status":"APPROVED","npciTxnId":"NPCI999"}
                        """))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("CollectRequest not found: COLNOTEXIST"));
    }

    @Test
    void outcome_missingApiKey_returns401() throws Exception {
        MockMvc securedMvc = MockMvcBuilders
                .standaloneSetup(new UpiCollectController(initiateCollectUseCase, collectRequestPort, "secret-key"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        securedMvc.perform(post("/upi/collect/outcome")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"collectId":"COL1234567890ABCDEF","status":"APPROVED","npciTxnId":"NPCI999"}
                        """))
           .andExpect(status().isUnauthorized());
    }
}
