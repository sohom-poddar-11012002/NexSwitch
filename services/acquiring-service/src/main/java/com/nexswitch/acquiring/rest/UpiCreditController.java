package com.nexswitch.acquiring.rest;

import com.nexswitch.domain.model.QRSession;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.port.outbound.QrSessionPort;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Optional;

// LEARN: UPICredit — NPCI sends a credit notification (not a debit-pull) to the merchant
//        acquirer after the PSP confirms payment. Money has already moved at this point.
//        The acquiring service must validate the session, mark it completed, and fire a
//        Kafka event so downstream services (webhook-dispatcher, settlement) can proceed.
@RestController
@RequestMapping("/upi")
public class UpiCreditController {

    private static final Logger log = LoggerFactory.getLogger(UpiCreditController.class);

    private final QrSessionPort       qrSessionPort;
    private final TransactionRepository transactionRepository;

    public UpiCreditController(QrSessionPort qrSessionPort,
                                TransactionRepository transactionRepository) {
        this.qrSessionPort         = qrSessionPort;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping("/credit")
    public ResponseEntity<?> credit(@Valid @RequestBody UpiCreditRequest req) {
        log.info("upi.credit.received npciTxnId={} txnRef={} amount={}",
                req.npciTxnId(), req.txnRef(), req.amount());

        Optional<QRSession> maybeSession = qrSessionPort.findByTxnRef(req.txnRef());
        if (maybeSession.isEmpty()) {
            log.warn("upi.credit.session_not_found txnRef={}", req.txnRef());
            return ResponseEntity.badRequest()
                    .body(new QrController.ErrorResponse("QR session expired or not found: " + req.txnRef()));
        }

        QRSession session = maybeSession.get();

        if (session.status() != QRSession.Status.PENDING) {
            log.warn("upi.credit.duplicate_or_completed txnRef={} status={}", req.txnRef(), session.status());
            return ResponseEntity.ok(new CreditAckResponse(req.txnRef(), "ALREADY_PROCESSED"));
        }

        if (session.isExpired()) {
            log.warn("upi.credit.expired_session txnRef={}", req.txnRef());
            qrSessionPort.update(session.withStatus(QRSession.Status.EXPIRED));
            return ResponseEntity.badRequest()
                    .body(new QrController.ErrorResponse("QR session expired: " + req.txnRef()));
        }

        // LEARN: Amount validation — compare in the same scale/rounding to avoid false mismatches.
        //        UPI credit amounts are always in INR paise-exact; BigDecimal compareTo ignores scale.
        BigDecimal credited = new BigDecimal(req.amount());
        if (credited.compareTo(session.amount().amount()) != 0) {
            log.warn("upi.credit.amount_mismatch txnRef={} expected={} received={}",
                    req.txnRef(), session.amount().amount(), credited);
            return ResponseEntity.badRequest()
                    .body(new QrController.ErrorResponse(
                            "Amount mismatch for txnRef " + req.txnRef()));
        }

        QRSession completed = session
                .withStatus(QRSession.Status.COMPLETED)
                .withNpciTxnId(req.npciTxnId());
        qrSessionPort.update(completed);
        // Session auto-deletes via Redis TTL; explicit delete cleans it up immediately
        qrSessionPort.delete(req.txnRef());

        log.info("upi.credit.completed txnRef={} npciTxnId={}", req.txnRef(), req.npciTxnId());
        return ResponseEntity.ok(new CreditAckResponse(req.txnRef(), "COMPLETED"));
    }

    record UpiCreditRequest(
            @NotBlank @Size(max = 35)   String npciTxnId,
            @NotBlank @Pattern(regexp = "[\\w.]+@[\\w]+", message = "must be a valid UPI VPA e.g. user@bank")
                                        String payerVpa,
            @NotBlank @Pattern(regexp = "[\\w.]+@[\\w]+", message = "must be a valid UPI VPA e.g. user@bank")
                                        String payeeVpa,
            @NotBlank @Pattern(regexp = "\\d+\\.\\d{2}", message = "must be a decimal with 2 places e.g. 100.00")
                                        String amount,
            @NotBlank                   String txnRef
    ) {}

    record CreditAckResponse(String txnRef, String status) {}
}
