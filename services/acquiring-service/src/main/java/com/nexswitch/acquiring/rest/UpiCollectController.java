package com.nexswitch.acquiring.rest;

import com.nexswitch.acquiring.rest.dto.*;
import com.nexswitch.domain.model.CollectRequest;
import com.nexswitch.domain.model.InitiateCollectResult;
import com.nexswitch.domain.model.vo.CollectId;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.model.vo.NpciTxnId;
import com.nexswitch.domain.port.inbound.InitiateCollectCommand;
import com.nexswitch.domain.port.inbound.InitiateCollectUseCase;
import com.nexswitch.domain.port.outbound.CollectRequestPort;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

// LEARN: UPI Collect (pull payment) — merchant initiates the debit request; customer sees
//        a push notification on their UPI app to approve or reject. The outcome arrives
//        asynchronously via POST /upi/collect/outcome from Mock NPCI webhook.
@RestController
@RequestMapping("/upi/collect")
public class UpiCollectController {

    private static final Logger log = LoggerFactory.getLogger(UpiCollectController.class);

    private final InitiateCollectUseCase initiateCollectUseCase;
    private final CollectRequestPort     collectRequestPort;
    private final String                 npciCallbackApiKey;

    public UpiCollectController(
            InitiateCollectUseCase initiateCollectUseCase,
            CollectRequestPort collectRequestPort,
            @Value("${npci.callback.api-key:}") String npciCallbackApiKey) {
        this.initiateCollectUseCase = initiateCollectUseCase;
        this.collectRequestPort     = collectRequestPort;
        this.npciCallbackApiKey     = npciCallbackApiKey;
    }

    @PostMapping
    public ResponseEntity<?> initiate(@Valid @RequestBody InitiateRequest req) {
        log.info("upi.collect.initiate merchantId={} payerVpa={} amount={}",
                req.merchantId(), req.payerVpa(), req.amount());

        InitiateCollectCommand command = new InitiateCollectCommand(
                MerchantId.of(req.merchantId()),
                req.payerVpa(),
                Money.of(new BigDecimal(req.amount()), Currency.getInstance(req.currency())),
                req.orderId(),
                req.expirySeconds() != null ? req.expirySeconds() : 180
        );

        InitiateCollectResult result = initiateCollectUseCase.execute(command);

        return switch (result) {
            case InitiateCollectResult.Initiated i -> {
                log.info("upi.collect.initiated collectId={}", i.collectId());
                yield ResponseEntity.ok(new InitiateResponse(i.collectId().value(), i.expiresAt()));
            }
            case InitiateCollectResult.Failed f -> {
                log.warn("upi.collect.failed reason={}", f.reason());
                yield ResponseEntity.badRequest().body(new ErrorResponse(f.reason()));
            }
        };
    }

    @PostMapping("/outcome")
    public ResponseEntity<?> outcome(
            // LEARN: Shared-secret header — the callback source (Mock NPCI / real NPCI) must
            //        present X-Npci-Api-Key. Without this check any caller who guesses a valid
            //        collectId could forge an APPROVED outcome. Key is blank in dev (check skipped).
            @RequestHeader(value = "X-Npci-Api-Key", required = false) String incomingKey,
            @Valid @RequestBody OutcomeRequest req) {
        if (!npciCallbackApiKey.isBlank() && !npciCallbackApiKey.equals(incomingKey)) {
            log.warn("upi.collect.outcome.unauthorized collectId={}", req.collectId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Missing or invalid X-Npci-Api-Key"));
        }
        log.info("upi.collect.outcome collectId={} status={} npciTxnId={}",
                req.collectId(), req.status(), req.npciTxnId());

        Optional<CollectRequest> maybeRequest = collectRequestPort.findByCollectId(CollectId.of(req.collectId()));
        if (maybeRequest.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("CollectRequest not found: " + req.collectId()));
        }

        CollectRequest request = maybeRequest.get();
        if (!request.isPending()) {
            return ResponseEntity.ok(new OutcomeAckResponse(req.collectId(), request.status().name()));
        }

        CollectRequest.Status newStatus = "APPROVED".equalsIgnoreCase(req.status())
                ? CollectRequest.Status.APPROVED
                : CollectRequest.Status.REJECTED;

        collectRequestPort.update(request.withStatus(newStatus).withNpciTxnId(new NpciTxnId(req.npciTxnId())));

        log.info("upi.collect.outcome.applied collectId={} status={}", req.collectId(), newStatus);
        return ResponseEntity.ok(new OutcomeAckResponse(req.collectId(), newStatus.name()));
    }
}
