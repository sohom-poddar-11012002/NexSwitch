package com.nexswitch.acquiring.rest;

import com.nexswitch.domain.model.QRGenerationResult;
import com.nexswitch.domain.port.inbound.GenerateQRUseCase;
import com.nexswitch.domain.port.inbound.GenerateStaticQRUseCase;
import com.nexswitch.domain.port.outbound.IdempotencyPort;
import com.nexswitch.domain.port.outbound.QrSessionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// LEARN: MockMvcBuilders.standaloneSetup — wires one controller + advice without a full Spring context;
//        test runs in milliseconds vs seconds for @SpringBootTest. setControllerAdvice() registers
//        the @RestControllerAdvice so validation errors are handled the same way as production.
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock GenerateQRUseCase       generateQRUseCase;
    @Mock GenerateStaticQRUseCase generateStaticQRUseCase;
    @Mock QrSessionPort           qrSessionPort;
    @Mock IdempotencyPort         idempotencyPort;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        lenient().when(idempotencyPort.acquire(any(), any())).thenReturn(true);
        mvc = MockMvcBuilders
                .standaloneSetup(new QrController(generateQRUseCase, generateStaticQRUseCase, qrSessionPort, idempotencyPort))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void missingMerchantId_returns400WithViolation() throws Exception {
        mvc.perform(post("/qr/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"terminalId":"TERM0001","amount":"100.00","currency":"INR","orderId":"O1"}
                        """))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.violations[?(@.field=='merchantId')]").exists());
    }

    @Test
    void blankMerchantId_returns400WithViolation() throws Exception {
        mvc.perform(post("/qr/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"merchantId":"","terminalId":"TERM0001","amount":"100.00","currency":"INR","orderId":"O1"}
                        """))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.violations[?(@.field=='merchantId')]").exists());
    }

    @Test
    void invalidAmountFormat_returns400WithViolation() throws Exception {
        mvc.perform(post("/qr/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"merchantId":"MERCH001","terminalId":"TERM0001",
                         "amount":"100","currency":"INR","orderId":"O1"}
                        """))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.violations[?(@.field=='amount')]").exists());
    }

    @Test
    void currencyNotThreeChars_returns400WithViolation() throws Exception {
        mvc.perform(post("/qr/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"merchantId":"MERCH001","terminalId":"TERM0001",
                         "amount":"100.00","currency":"INRX","orderId":"O1"}
                        """))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.violations[?(@.field=='currency')]").exists());
    }

    @Test
    void malformedJson_returns400WithErrorField() throws Exception {
        mvc.perform(post("/qr/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not json at all"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void validRequest_passesToUseCase() throws Exception {
        when(generateQRUseCase.execute(any()))
                .thenReturn(new QRGenerationResult.Failed("unknown merchant"));

        mvc.perform(post("/qr/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"merchantId":"MERCH0000999","terminalId":"TERM0001",
                         "amount":"6000.00","currency":"INR","orderId":"order-001"}
                        """))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("unknown merchant"));
    }
}
