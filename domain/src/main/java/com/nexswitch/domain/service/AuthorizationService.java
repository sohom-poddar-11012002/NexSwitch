package com.nexswitch.domain.service;

import com.nexswitch.domain.model.*;
import com.nexswitch.domain.model.event.DomainEvent;
import com.nexswitch.domain.model.event.TransactionInitiatedEvent;
import com.nexswitch.domain.model.vo.AuthorizationCode;
import com.nexswitch.domain.port.inbound.AuthorizationCommand;
import com.nexswitch.domain.port.inbound.ProcessPaymentUseCase;
import com.nexswitch.domain.port.outbound.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

// LEARN: OrchestratingService — the service coordinates ports but contains no business logic of its own.
//        Business rules live in the domain objects: MerchantProfile.isActive(), Terminal.isActive(),
//        Money.isGreaterThan(). The service only calls them and routes results.
//
// LEARN: FailFast — each validation step returns early on failure. This avoids partial state writes
//        and makes the happy-path code readable: if you reach the network call, all guards passed.
public class AuthorizationService implements ProcessPaymentUseCase {

    // LEARN: ISO 8583 response codes — two-digit strings defined by the ISO 8583 standard.
    //        "57" = Transaction not permitted, "58" = Transaction not permitted for terminal,
    //        "61" = Exceeds withdrawal limit, "82" = ARQC verification failed,
    //        "94" = Duplicate transmission.  Field 39 in the 0110 response carries this code.
    private static final String RC_BIN_NOT_FOUND         = "57";
    private static final String RC_DUPLICATE             = "94";
    private static final String RC_TERMINAL_NOT_ACTIVE   = "58";
    private static final String RC_MERCHANT_NOT_ACTIVE   = "57";
    private static final String RC_EXCEEDS_LIMIT         = "61";
    private static final String RC_ARQC_FAILED           = "82";
    private static final String RC_FRAUD_DECLINE         = "59";
    private static final String RC_REPLAY_ATTACK         = "62";
    private static final String RC_FALLBACK_EXCEEDED     = "57";

    // LEARN: FraudThreshold — probability above which we block rather than forward to network.
    //        0.80 means "80% chance of fraud". Production tunes this per card product / MCC.
    private static final BigDecimal FRAUD_THRESHOLD = new BigDecimal("0.80");

    // LEARN: IdempotencyTTL — window during which duplicate STAN+terminal retransmissions are blocked.
    //        ISO 8583 mandates STAN uniqueness per terminal per day; 5 min covers any realistic retry.
    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(5);

    private final BinLookupPort         binLookupPort;
    private final IdempotencyPort       idempotencyPort;
    private final TerminalRepository    terminalRepository;
    private final MerchantRepository    merchantRepository;
    private final HsmPort               hsmPort;
    private final FraudScoringPort      fraudScoringPort;
    private final AuthorizationPort     authorizationPort;
    private final TransactionRepository transactionRepository;
    private final TransactionStateMachine stateMachine;
    private final AuditPort             auditPort;
    private final AtcWatermarkPort      atcWatermarkPort;
    private final FallbackCounterPort   fallbackCounterPort;
    private final Clock clock;

    private static final AtcWatermarkPort NO_OP_ATC = new AtcWatermarkPort() {
        @Override public boolean isAtcFresh(com.nexswitch.domain.model.vo.PanHash p, int a) { return true; }
        @Override public void updateWatermark(com.nexswitch.domain.model.vo.PanHash p, int a) {}
    };

    private static final FallbackCounterPort NO_OP_FALLBACK = new FallbackCounterPort() {
        @Override public int getAndIncrementFallbackCount(
                com.nexswitch.domain.model.vo.PanHash p,
                com.nexswitch.domain.model.vo.TerminalId t) { return 0; }
    };

    /** Backward-compat constructor for tests that don't inject ATC or fallback ports. */
    public AuthorizationService(
            BinLookupPort binLookupPort,
            IdempotencyPort idempotencyPort,
            TerminalRepository terminalRepository,
            MerchantRepository merchantRepository,
            HsmPort hsmPort,
            FraudScoringPort fraudScoringPort,
            AuthorizationPort authorizationPort,
            TransactionRepository transactionRepository,
            TransactionStateMachine stateMachine) {
        this(binLookupPort, idempotencyPort, terminalRepository, merchantRepository,
             hsmPort, fraudScoringPort, authorizationPort, transactionRepository,
             stateMachine, Clock.systemUTC(), (a, b, c, d, e, f, g, h) -> {},
             NO_OP_ATC, NO_OP_FALLBACK);
    }

    /** Backward-compat constructor for tests that inject audit but not ATC/fallback ports. */
    public AuthorizationService(
            BinLookupPort binLookupPort,
            IdempotencyPort idempotencyPort,
            TerminalRepository terminalRepository,
            MerchantRepository merchantRepository,
            HsmPort hsmPort,
            FraudScoringPort fraudScoringPort,
            AuthorizationPort authorizationPort,
            TransactionRepository transactionRepository,
            TransactionStateMachine stateMachine,
            Clock clock,
            AuditPort auditPort) {
        this(binLookupPort, idempotencyPort, terminalRepository, merchantRepository,
             hsmPort, fraudScoringPort, authorizationPort, transactionRepository,
             stateMachine, clock, auditPort,
             NO_OP_ATC, NO_OP_FALLBACK);
    }

    /** Full constructor with all security ports. */
    public AuthorizationService(
            BinLookupPort binLookupPort,
            IdempotencyPort idempotencyPort,
            TerminalRepository terminalRepository,
            MerchantRepository merchantRepository,
            HsmPort hsmPort,
            FraudScoringPort fraudScoringPort,
            AuthorizationPort authorizationPort,
            TransactionRepository transactionRepository,
            TransactionStateMachine stateMachine,
            Clock clock,
            AuditPort auditPort,
            AtcWatermarkPort atcWatermarkPort,
            FallbackCounterPort fallbackCounterPort) {
        this.binLookupPort         = binLookupPort;
        this.idempotencyPort       = idempotencyPort;
        this.terminalRepository    = terminalRepository;
        this.merchantRepository    = merchantRepository;
        this.hsmPort               = hsmPort;
        this.fraudScoringPort      = fraudScoringPort;
        this.authorizationPort     = authorizationPort;
        this.transactionRepository = transactionRepository;
        this.stateMachine          = stateMachine;
        this.auditPort             = auditPort;
        this.atcWatermarkPort      = atcWatermarkPort;
        this.fallbackCounterPort   = fallbackCounterPort;
        this.clock                 = clock;
    }

    @Override
    public AuthorizationResult execute(AuthorizationCommand cmd) {

        // ── Step 1: BIN lookup ─────────────────────────────────────────────
        // Fail fast: if we don't recognise the BIN we cannot route or earn MDR.
        Optional<BinInfo> binInfo = binLookupPort.lookup(cmd.bin6());
        if (binInfo.isEmpty()) {
            return new AuthorizationResult.Declined(RC_BIN_NOT_FOUND, "BIN_NOT_FOUND");
        }

        // ── Step 2: Idempotency ────────────────────────────────────────────
        // LEARN: ISO 8583 STAN is unique per terminal per calendar day (Field 11).
        //        Combining terminalId+STAN gives a globally unique key for the message.
        String idempotencyKey = cmd.terminalId().value() + ":" + cmd.stan().value();
        if (!idempotencyPort.acquire(idempotencyKey, IDEMPOTENCY_TTL)) {
            return new AuthorizationResult.Declined(RC_DUPLICATE, "DUPLICATE_TRANSMISSION");
        }

        // ── Step 3: Terminal active ────────────────────────────────────────
        Terminal terminal = terminalRepository.findById(cmd.terminalId()).orElse(null);
        if (terminal == null || !terminal.isActive()) {
            return new AuthorizationResult.Declined(RC_TERMINAL_NOT_ACTIVE, "TERMINAL_NOT_ACTIVE");
        }

        // ── Step 4: Merchant active ────────────────────────────────────────
        MerchantProfile merchant = merchantRepository.findById(cmd.merchantId()).orElse(null);
        if (merchant == null || !merchant.isActive()) {
            return new AuthorizationResult.Declined(RC_MERCHANT_NOT_ACTIVE, "MERCHANT_NOT_ACTIVE");
        }

        // ── Step 5: Per-transaction limit check ────────────────────────────
        // LEARN: Using Money.isGreaterThan() — BigDecimal comparison respects scale; never use > on BigDecimal directly.
        if (cmd.amount().isGreaterThan(merchant.perTransactionLimit())) {
            return new AuthorizationResult.Declined(RC_EXCEEDS_LIMIT, "EXCEEDS_TRANSACTION_LIMIT");
        }

        // ── Create INITIATED transaction ───────────────────────────────────
        Transaction txn = Transaction.builder()
                .id(cmd.transactionId())
                .merchantId(cmd.merchantId())
                .terminalId(cmd.terminalId())
                .amount(cmd.amount())
                .network(cmd.network())
                .paymentMethod(cmd.paymentMethod())
                .panHash(cmd.panHash())
                .stan(cmd.stan())
                .cardLast4(cmd.cardLast4())
                .status(TransactionStatus.INITIATED)
                .createdAt(Instant.now(clock))
                .build();
        txn.raiseEvent(DomainEvent.of(
                "transaction.initiated", txn.id().toString(), "TRANSACTION",
                new TransactionInitiatedEvent(
                        txn.id(), txn.merchantId(), txn.amount(), txn.network(), txn.paymentMethod())));
        txn = transactionRepository.save(txn);
        auditPort.record("TRANSACTION_INITIATED", "authorization-service",
                txn.id(), txn.id().toString(), "TRANSACTION",
                null, TransactionStatus.INITIATED.name(), null);

        // ── Step 6a: DUKPT PIN block translation ──────────────────────────
        // LEARN: PIN block translation is a two-step HSM operation: (1) use BDK+KSN to derive the
        //        unique terminal transaction key and decrypt Field 52 inside the HSM; (2) re-encrypt
        //        the plaintext PIN under the ZPK for onward transit. The PIN is never in plaintext
        //        outside the HSM boundary. Skip when pinBlock or KSN are absent (no-PIN flows).
        if (cmd.pinBlock() != null && cmd.ksn() != null) {
            hsmPort.translatePinBlock(cmd.pinBlock(), bytesToHex(cmd.ksn()), "zpk");
        }

        // ── Step 6b: ATC replay attack detection ──────────────────────────
        // LEARN: ATC watermark check — if ATC ≤ last_seen_atc the chip has been replayed.
        //        Cloned EMV cards are caught here because they cannot advance the ATC past what
        //        the genuine card already recorded. Response code "62" = Restricted card.
        if (cmd.emvData() != null && !atcWatermarkPort.isAtcFresh(cmd.panHash(), cmd.emvData().atc())) {
            Transaction declined = txn.decline(RC_REPLAY_ATTACK);
            transactionRepository.save(declined);
            return new AuthorizationResult.Declined(RC_REPLAY_ATTACK, "REPLAY_ATTACK_SUSPECTED");
        }

        // ── Step 6c: Magstripe fallback limit ──────────────────────────────
        // LEARN: Magstripe fallback limit — Visa allows max 2, Mastercard max 3 per card/terminal/day.
        //        Attackers induce chip failure to expose PAN in plaintext; this limit stops them.
        if (cmd.paymentMethod() == com.nexswitch.domain.model.PaymentMethod.MAGSTRIPE) {
            int fallbackCount = fallbackCounterPort.getAndIncrementFallbackCount(cmd.panHash(), cmd.terminalId());
            int maxFallbacks  = cmd.network() == com.nexswitch.domain.model.PaymentNetwork.VISA ? 2 : 3;
            if (fallbackCount >= maxFallbacks) {
                Transaction declined = txn.decline(RC_FALLBACK_EXCEEDED);
                transactionRepository.save(declined);
                return new AuthorizationResult.Declined(RC_FALLBACK_EXCEEDED, "FALLBACK_LIMIT_EXCEEDED");
            }
        }

        // ── Step 6d: HSM ARQC verification ────────────────────────────────
        // LEARN: ARQC (Application Request Cryptogram) is an 8-byte MAC generated by the EMV chip
        //        using a session key derived from the card's master key and ATC (Application Transaction Counter).
        //        Verifying it proves the physical card (not just stolen card data) was present.
        //        Only relevant for EMV chip transactions — skip for MSR swipe (no emvData).
        // LEARN: ARQC (Application Request Cryptogram) — the EMV chip MACs the CDOL1 transaction
        //        data using a session key derived from the Issuer Master Key + ATC. Verifying it
        //        proves the physical card (not cloned stripe data) was present. ATC is the
        //        per-transaction counter that makes each session key unique.
        if (cmd.emvData() != null) {
            boolean arqcValid = hsmPort.verifyArqc(
                    cmd.panHash(),
                    cmd.emvData().arqc(),
                    cmd.emvData().atc(),
                    cmd.emvData().transactionData());
            if (!arqcValid) {
                Transaction declined = txn.decline(RC_ARQC_FAILED);
                transactionRepository.save(declined);
                return new AuthorizationResult.Declined(RC_ARQC_FAILED, "ARQC_VERIFICATION_FAILED");
            }
            // Update ATC watermark after successful ARQC verification
            atcWatermarkPort.updateWatermark(cmd.panHash(), cmd.emvData().atc());
        }

        // ── Step 7: Fraud score ────────────────────────────────────────────
        // LEARN: budget=500ms — we cannot make the cardholder wait >2s total; ML inference gets 500ms.
        //        If the ML service times out, score is absent → we forward to network (fail open).
        //        Blocking on ML timeout would degrade auth success rate, hurting merchants more than fraud.
        FraudScoringContext fraudCtx = new FraudScoringContext(
                cmd.panHash(), cmd.amount(), merchant.mcc(),
                cmd.network(), cmd.paymentMethod(), Instant.now(clock));
        Optional<BigDecimal> score = fraudScoringPort.score(fraudCtx, Duration.ofMillis(500));
        if (score.isPresent() && score.get().compareTo(FRAUD_THRESHOLD) >= 0) {
            Transaction declined = txn.decline(RC_FRAUD_DECLINE);
            transactionRepository.save(declined);
            return new AuthorizationResult.Blocked("HIGH_FRAUD_SCORE:" + score.get().toPlainString());
        }

        // ── → AUTHORIZATION_PENDING ────────────────────────────────────────
        txn = stateMachine.transition(txn, TransactionStatus.AUTHORIZATION_PENDING);
        txn = transactionRepository.save(txn);

        // ── Step 8: Network authorization ─────────────────────────────────
        // LEARN: The switch forwards the 0100 to Visa/MC via a persistent TCP connection (see §8).
        //        The network validates the ARQC with the issuer's HSM and returns 0110 with Field 39.
        AuthorizationResult result = authorizationPort.authorize(txn);

        // ── Step 9: ARPC generation (EMV chip only) ────────────────────────
        // LEARN: ARPC (Application Response Cryptogram) — the issuer generates this so the terminal
        //        can verify the 0110 came from a real issuer. Method 1: ARPC = 3DES(CSK, ARQC XOR ARC).
        //        ARC = "00" approved, "01" declined. Skipped for non-chip flows (emvData is null).
        if (result instanceof AuthorizationResult.Approved a && cmd.emvData() != null) {
            byte[] arpc = hsmPort.generateArpc(cmd.emvData().arqc(), cmd.emvData().atc(),
                    result instanceof AuthorizationResult.Approved ? "00" : "01");
            result = new AuthorizationResult.Approved(a.authCode(), a.authorizedAt(), arpc);
        }

        // ── Update transaction to final state ──────────────────────────────
        // LEARN: Sealed interface switch — compiler forces all cases; no default needed.
        //        This is the exhaustive handling guarantee that justifies using sealed types.
        Transaction finalTxn = switch (result) {
            case AuthorizationResult.Approved a  -> txn.authorize(a.authCode());
            case AuthorizationResult.Declined d  -> txn.decline(d.responseCode());
            case AuthorizationResult.Unknown  u  -> stateMachine.transition(txn, TransactionStatus.UNKNOWN);
            case AuthorizationResult.Blocked  b  -> txn.decline(RC_FRAUD_DECLINE);
        };
        transactionRepository.save(finalTxn);
        auditPort.record("TRANSACTION_FINAL_STATE", "authorization-service",
                finalTxn.id(), finalTxn.id().toString(), "TRANSACTION",
                TransactionStatus.AUTHORIZATION_PENDING.name(), finalTxn.status().name(), null);

        return result;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }
}
