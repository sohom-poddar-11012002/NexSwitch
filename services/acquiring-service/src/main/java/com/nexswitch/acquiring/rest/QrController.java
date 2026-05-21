package com.nexswitch.acquiring.rest;

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
import com.nexswitch.domain.port.outbound.QrSessionPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;

@RestController
@RequestMapping("/qr")
public class QrController {

    private static final Logger log = LoggerFactory.getLogger(QrController.class);

    private final GenerateQRUseCase       generateQRUseCase;
    private final GenerateStaticQRUseCase generateStaticQRUseCase;
    private final QrSessionPort           qrSessionPort;

    public QrController(GenerateQRUseCase generateQRUseCase,
                        GenerateStaticQRUseCase generateStaticQRUseCase,
                        QrSessionPort qrSessionPort) {
        this.generateQRUseCase       = generateQRUseCase;
        this.generateStaticQRUseCase = generateStaticQRUseCase;
        this.qrSessionPort           = qrSessionPort;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@Valid @RequestBody GenerateRequest req) {
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
                yield ResponseEntity.ok(new GenerateResponse(
                        g.txnRef(), g.qrImageBase64(), g.expiresAt()));
            }
            case QRGenerationResult.Failed f -> {
                log.warn("qr.generate.failed reason={}", f.reason());
                yield ResponseEntity.badRequest().body(new ErrorResponse(f.reason()));
            }
        };
    }

    @GetMapping("/status/{txnRef}")
    public ResponseEntity<?> status(@PathVariable String txnRef) {
        Optional<QRSession> session = qrSessionPort.findByTxnRef(txnRef);
        if (session.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("QR session not found or expired: " + txnRef));
        }
        QRSession s = session.get();
        return ResponseEntity.ok(new StatusResponse(
                s.txnRef(), s.status().name(), s.npciTxnId(), s.expiresAt()));
    }

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

    // ── Request / response records ────────────────────────────────────────────

    record GenerateRequest(
            @NotBlank @Size(max = 16)  String merchantId,
            @NotBlank @Size(max = 16)  String terminalId,
            @NotBlank @Pattern(regexp = "\\d+\\.\\d{2}", message = "must be a decimal with 2 places e.g. 100.00")
                                       String amount,
            @NotBlank @Size(min = 3, max = 3, message = "must be a 3-letter ISO 4217 currency code")
                                       String currency,
            @NotBlank @Size(max = 64)  String orderId
    ) {}

    record GenerateResponse(String txnRef, String qrImageBase64, Instant expiresAt) {}
    record StaticQrResponse(String qrImageBase64, String vpa, String upiString) {}
    record StatusResponse(String txnRef, String status, String npciTxnId, Instant expiresAt) {}
    record ErrorResponse(String reason) {}
}
