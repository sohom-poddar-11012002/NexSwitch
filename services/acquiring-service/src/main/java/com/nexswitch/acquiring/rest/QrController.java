package com.nexswitch.acquiring.rest;

import com.nexswitch.acquiring.rest.dto.*;
import com.nexswitch.domain.model.QRGenerationResult;
import com.nexswitch.domain.model.QRSession;
import com.nexswitch.domain.model.StaticQRResult;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.model.vo.TerminalId;
import com.nexswitch.domain.port.inbound.GenerateQRUseCase;
import com.nexswitch.domain.port.inbound.GenerateStaticQRCommand;
import com.nexswitch.domain.port.inbound.GenerateStaticQRUseCase;
import com.nexswitch.domain.port.inbound.QRGenerationCommand;
import com.nexswitch.domain.port.outbound.IdempotencyPort;
import com.nexswitch.domain.port.outbound.QrSessionPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.Optional;

@Tag(name = "QR Payments", description = "Dynamic and static QR code generation and status")
@Validated
@RestController
@RequestMapping("/qr")
public class QrController {

    private static final Logger log = LoggerFactory.getLogger(QrController.class);

    private final GenerateQRUseCase       generateQRUseCase;
    private final GenerateStaticQRUseCase generateStaticQRUseCase;
    private final QrSessionPort           qrSessionPort;
    private final IdempotencyPort         idempotencyPort;

    public QrController(GenerateQRUseCase generateQRUseCase,
                        GenerateStaticQRUseCase generateStaticQRUseCase,
                        QrSessionPort qrSessionPort,
                        IdempotencyPort idempotencyPort) {
        this.generateQRUseCase       = generateQRUseCase;
        this.generateStaticQRUseCase = generateStaticQRUseCase;
        this.qrSessionPort           = qrSessionPort;
        this.idempotencyPort         = idempotencyPort;
    }

    @Operation(summary = "Generate dynamic QR", description = "Creates a time-limited QR code for a specific order amount")
    @PostMapping("/generate")
    public ResponseEntity<?> generate(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody GenerateRequest req) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()
                && !idempotencyPort.acquire(idempotencyKey, Duration.ofMinutes(5))) {
            log.info("qr.generate.duplicate_request idempotencyKey={}", idempotencyKey);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Duplicate request"));
        }
        log.info("qr.generate.request merchantId={} orderId={}", req.merchantId(), req.orderId());

        QRGenerationCommand command = new QRGenerationCommand(
                MerchantId.of(req.merchantId()),
                new TerminalId(req.terminalId()),
                Money.of(new BigDecimal(req.amount()), Currency.getInstance(req.currency())),
                req.orderId()
        );

        QRGenerationResult result = generateQRUseCase.execute(command);

        return switch (result) {
            case QRGenerationResult.Generated g -> {
                log.info("qr.generate.success txnRef={}", g.txnRef());
                yield ResponseEntity.ok(new GenerateResponse(g.txnRef().value(), g.qrImageBase64(), g.expiresAt()));
            }
            case QRGenerationResult.Failed f -> {
                log.warn("qr.generate.failed reason={}", f.reason());
                yield ResponseEntity.badRequest().body(new ErrorResponse(f.reason()));
            }
        };
    }

    @Operation(summary = "QR session status", description = "Returns current status of a QR payment session by txnRef")
    @GetMapping("/status/{txnRef}")
    public ResponseEntity<?> status(@PathVariable String txnRef) {
        Optional<QRSession> session = qrSessionPort.findByTxnRef(txnRef);
        if (session.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("QR session not found or expired: " + txnRef));
        }
        QRSession s = session.get();
        return ResponseEntity.ok(new QrStatusResponse(
                s.txnRef().value(),
                s.status().name(),
                s.npciTxnId() != null ? s.npciTxnId().value() : null,
                s.expiresAt()));
    }

    @Operation(summary = "Generate static QR", description = "Returns a static VPA-based QR code for a merchant (no expiry)")
    @GetMapping("/static/{merchantId}")
    public ResponseEntity<?> staticQr(@PathVariable String merchantId) {
        StaticQRResult result = generateStaticQRUseCase.execute(
                new GenerateStaticQRCommand(MerchantId.of(merchantId)));
        return switch (result) {
            case StaticQRResult.Generated g -> ResponseEntity.ok(
                    new StaticQrResponse(g.qrImageBase64(), g.vpa(), g.upiString()));
            case StaticQRResult.Failed f    -> ResponseEntity.badRequest()
                    .body(new ErrorResponse(f.reason()));
        };
    }
}
