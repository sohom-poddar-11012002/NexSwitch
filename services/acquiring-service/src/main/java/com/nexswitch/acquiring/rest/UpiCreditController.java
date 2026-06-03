package com.nexswitch.acquiring.rest;

import com.nexswitch.acquiring.rest.dto.CreditAckResponse;
import com.nexswitch.acquiring.rest.dto.ErrorResponse;
import com.nexswitch.acquiring.rest.dto.UpiCreditRequest;
import com.nexswitch.domain.model.QRSession;
import com.nexswitch.domain.model.vo.NpciTxnId;
import com.nexswitch.domain.port.outbound.QrSessionPort;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Optional;

// LEARN: UPICredit — NPCI sends a credit notification (not a debit-pull) to the merchant
//        acquirer after the PSP confirms payment. Money has already moved at this point.
//        The acquiring service must validate the session, mark it completed, and fire a
//        Kafka event so downstream services (webhook-dispatcher, settlement) can proceed.
@Tag(name = "UPI Credit", description = "NPCI credit notification handler for completed QR payments")
@Validated
@RestController
@RequestMapping("/upi")
public class UpiCreditController {

    private static final Logger log = LoggerFactory.getLogger(UpiCreditController.class);

    private final QrSessionPort        qrSessionPort;
    private final TransactionRepository transactionRepository;

    public UpiCreditController(QrSessionPort qrSessionPort,
                                TransactionRepository transactionRepository) {
        this.qrSessionPort         = qrSessionPort;
        this.transactionRepository = transactionRepository;
    }

    @Operation(summary = "UPI credit notification", description = "Marks QR session completed when NPCI confirms payment; validates amount and session state")
    @PostMapping("/credit")
    public ResponseEntity<?> credit(@Valid @RequestBody UpiCreditRequest req) {
        log.info("upi.credit.received npciTxnId={} txnRef={} amount={}",
                req.npciTxnId(), req.txnRef(), req.amount());

        Optional<QRSession> maybeSession = qrSessionPort.findByTxnRef(req.txnRef());
        if (maybeSession.isEmpty()) {
            log.warn("upi.credit.session_not_found txnRef={}", req.txnRef());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("QR session expired or not found: " + req.txnRef()));
        }

        QRSession session = maybeSession.get();

        if (!session.isPending()) {
            log.warn("upi.credit.duplicate_or_completed txnRef={} status={}", req.txnRef(), session.status());
            return ResponseEntity.ok(new CreditAckResponse(req.txnRef(), "ALREADY_PROCESSED"));
        }

        if (session.isExpired()) {
            log.warn("upi.credit.expired_session txnRef={}", req.txnRef());
            qrSessionPort.update(session.withStatus(QRSession.Status.EXPIRED));
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("QR session expired: " + req.txnRef()));
        }

        // LEARN: Amount validation — compare in the same scale/rounding to avoid false mismatches.
        //        UPI credit amounts are always in INR paise-exact; BigDecimal compareTo ignores scale.
        if (!session.amount().matches(new BigDecimal(req.amount()))) {
            log.warn("upi.credit.amount_mismatch txnRef={} expected={} received={}",
                    req.txnRef(), session.amount().amount(), req.amount());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Amount mismatch for txnRef " + req.txnRef()));
        }

        QRSession completed = session
                .withStatus(QRSession.Status.COMPLETED)
                .withNpciTxnId(new NpciTxnId(req.npciTxnId()));
        qrSessionPort.update(completed);
        // LEARN: Session stays in Redis until TTL so merchants can poll status after credit.
        //        Immediate delete would cause a 404 on the very next GET /qr/status call.

        log.info("upi.credit.completed txnRef={} npciTxnId={}", req.txnRef(), req.npciTxnId());
        return ResponseEntity.ok(new CreditAckResponse(req.txnRef(), "COMPLETED"));
    }
}
