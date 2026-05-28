package com.nexswitch.domain.service;

import com.nexswitch.domain.model.*;
import com.nexswitch.domain.model.vo.*;
import com.nexswitch.domain.port.inbound.AuthorizationCommand;
import static org.mockito.ArgumentMatchers.anyInt;
import com.nexswitch.domain.port.outbound.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

// LEARN: UnitTest with Mockito — every dependency is a mock; the test owns all behaviour.
//        Each test method covers exactly one failure mode (one step in the validation chain).
//        This gives pinpoint failure messages when something breaks.
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    private static final Currency INR  = Currency.getInstance("INR");
    private static final String   BIN6 = "411111";

    @Mock BinLookupPort       binLookupPort;
    @Mock IdempotencyPort     idempotencyPort;
    @Mock TerminalRepository  terminalRepository;
    @Mock MerchantRepository  merchantRepository;
    @Mock HsmPort             hsmPort;
    @Mock FraudScoringPort    fraudScoringPort;
    @Mock AuthorizationPort   authorizationPort;
    @Mock TransactionRepository transactionRepository;

    AuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationService(
                binLookupPort, idempotencyPort, terminalRepository,
                merchantRepository, hsmPort, fraudScoringPort,
                authorizationPort, transactionRepository,
                new TransactionStateMachine()
        );
    }

    // ── Step 1: BIN lookup ──────────────────────────────────────────────────

    @Test
    void givenUnknownBin_thenDeclined_57() {
        when(binLookupPort.lookup(BIN6)).thenReturn(Optional.empty());

        AuthorizationResult result = service.execute(validCommand());

        assertThat(result).isInstanceOf(AuthorizationResult.Declined.class);
        assertThat(((AuthorizationResult.Declined) result).responseCode()).isEqualTo("57");
    }

    // ── Step 2: Idempotency ─────────────────────────────────────────────────

    @Test
    void givenDuplicateStan_thenDeclined_94() {
        stubBinFound();
        when(idempotencyPort.acquire(anyString(), any(Duration.class))).thenReturn(false);

        AuthorizationResult result = service.execute(validCommand());

        assertThat(result).isInstanceOf(AuthorizationResult.Declined.class);
        assertThat(((AuthorizationResult.Declined) result).responseCode()).isEqualTo("94");
    }

    // ── Step 3: Terminal active ─────────────────────────────────────────────

    @Test
    void givenTerminalNotFound_thenDeclined_58() {
        stubBinFound();
        stubIdempotencyAcquired();
        when(terminalRepository.findById(any())).thenReturn(Optional.empty());

        AuthorizationResult result = service.execute(validCommand());

        assertThat(result).isInstanceOf(AuthorizationResult.Declined.class);
        assertThat(((AuthorizationResult.Declined) result).responseCode()).isEqualTo("58");
    }

    @Test
    void givenTerminalSuspended_thenDeclined_58() {
        stubBinFound();
        stubIdempotencyAcquired();
        when(terminalRepository.findById(any())).thenReturn(Optional.of(
                new Terminal(TerminalId.of("TERM0042"), MerchantId.of("MERCH0000999"),
                             Terminal.Status.SUSPENDED)));

        AuthorizationResult result = service.execute(validCommand());

        assertThat(result).isInstanceOf(AuthorizationResult.Declined.class);
        assertThat(((AuthorizationResult.Declined) result).responseCode()).isEqualTo("58");
    }

    // ── Step 4: Merchant active ─────────────────────────────────────────────

    @Test
    void givenMerchantNotFound_thenDeclined_57() {
        stubBinFound();
        stubIdempotencyAcquired();
        stubActiveTerminal();
        when(merchantRepository.findById(any())).thenReturn(Optional.empty());

        AuthorizationResult result = service.execute(validCommand());

        assertThat(result).isInstanceOf(AuthorizationResult.Declined.class);
        assertThat(((AuthorizationResult.Declined) result).responseCode()).isEqualTo("57");
    }

    @Test
    void givenMerchantSuspended_thenDeclined_57() {
        stubBinFound();
        stubIdempotencyAcquired();
        stubActiveTerminal();
        when(merchantRepository.findById(any())).thenReturn(Optional.of(
                merchantProfile(MerchantProfile.Status.SUSPENDED, "100000.00")));

        AuthorizationResult result = service.execute(validCommand());

        assertThat(result).isInstanceOf(AuthorizationResult.Declined.class);
        assertThat(((AuthorizationResult.Declined) result).responseCode()).isEqualTo("57");
    }

    // ── Step 5: Limit check ─────────────────────────────────────────────────

    @Test
    void givenAmountExceedsPerTransactionLimit_thenDeclined_61() {
        stubBinFound();
        stubIdempotencyAcquired();
        stubActiveTerminal();
        // Limit ₹5,000 but command amount is ₹6,000
        when(merchantRepository.findById(any())).thenReturn(Optional.of(
                merchantProfile(MerchantProfile.Status.ACTIVE, "5000.00")));

        AuthorizationResult result = service.execute(validCommand());

        assertThat(result).isInstanceOf(AuthorizationResult.Declined.class);
        assertThat(((AuthorizationResult.Declined) result).responseCode()).isEqualTo("61");
    }

    // ── Step 6: HSM ARQC verify ─────────────────────────────────────────────

    @Test
    void givenArqcVerificationFails_thenDeclined_82() {
        stubBinFound();
        stubIdempotencyAcquired();
        stubActiveTerminal();
        stubActiveMerchant();
        stubTransactionSave();
        when(hsmPort.verifyArqc(any(), any(), anyInt(), any())).thenReturn(false);

        AuthorizationResult result = service.execute(commandWithEmvData());

        assertThat(result).isInstanceOf(AuthorizationResult.Declined.class);
        assertThat(((AuthorizationResult.Declined) result).responseCode()).isEqualTo("82");
    }

    // ── Step 7: Fraud score ─────────────────────────────────────────────────

    @Test
    void givenHighFraudScore_thenBlocked() {
        stubBinFound();
        stubIdempotencyAcquired();
        stubActiveTerminal();
        stubActiveMerchant();
        stubTransactionSave();
        when(hsmPort.verifyArqc(any(), any(), anyInt(), any())).thenReturn(true);
        when(fraudScoringPort.score(any(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.92")));

        AuthorizationResult result = service.execute(commandWithEmvData());

        assertThat(result).isInstanceOf(AuthorizationResult.Blocked.class);
    }

    // ── Step 8: Network auth ────────────────────────────────────────────────

    @Test
    void givenNetworkApproves_thenApproved() {
        stubBinFound();
        stubIdempotencyAcquired();
        stubActiveTerminal();
        stubActiveMerchant();
        stubTransactionSave();
        stubNoFraud();
        AuthorizationCode authCode = AuthorizationCode.of("AUTH01");
        when(authorizationPort.authorize(any())).thenReturn(
                new AuthorizationResult.Approved(authCode, java.time.Instant.now(), null));

        AuthorizationResult result = service.execute(validCommand());

        assertThat(result).isInstanceOf(AuthorizationResult.Approved.class);
        assertThat(((AuthorizationResult.Approved) result).authCode()).isEqualTo(authCode);
    }

    @Test
    void givenNetworkDeclines_thenDeclined_05() {
        stubBinFound();
        stubIdempotencyAcquired();
        stubActiveTerminal();
        stubActiveMerchant();
        stubTransactionSave();
        stubNoFraud();
        when(authorizationPort.authorize(any())).thenReturn(
                new AuthorizationResult.Declined("05", "DO_NOT_HONOR"));


        AuthorizationResult result = service.execute(validCommand());

        assertThat(result).isInstanceOf(AuthorizationResult.Declined.class);
        assertThat(((AuthorizationResult.Declined) result).responseCode()).isEqualTo("05");
    }

    // ── Golden path ─────────────────────────────────────────────────────────

    @Test
    void goldenPath_allStepsPass_returnsApproved() {
        stubBinFound();
        stubIdempotencyAcquired();
        stubActiveTerminal();
        stubActiveMerchant();
        stubTransactionSave();
        when(hsmPort.verifyArqc(any(), any(), anyInt(), any())).thenReturn(true);
        when(hsmPort.generateArpc(any(), anyInt(), any())).thenReturn(new byte[8]);
        stubNoFraud();
        AuthorizationCode authCode = AuthorizationCode.of("AUTH01");
        when(authorizationPort.authorize(any())).thenReturn(
                new AuthorizationResult.Approved(authCode, java.time.Instant.now(), null));

        AuthorizationResult result = service.execute(commandWithEmvData());

        assertThat(result).isInstanceOf(AuthorizationResult.Approved.class);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private AuthorizationCommand validCommand() {
        return new AuthorizationCommand(
                UUID.randomUUID(),
                MerchantId.of("MERCH0000999"),
                TerminalId.of("TERM0042"),
                BIN6,
                PanHash.of("a".repeat(64)),
                Money.of("6000.00", INR),
                PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP,
                SystemTraceAuditNumber.of("000042"),
                null,
                null,
                null,   // ksn: null for non-PIN flows
                "05",
                "1111", // cardLast4
                null,   // cavv: null for non-3DS flows
                null    // eci:  null for non-3DS flows
        );
    }

    private AuthorizationCommand commandWithEmvData() {
        // 8-byte ARQC (Tag 9F26), ATC=1, 33-byte CDOL1 transaction data
        EmvData emvData = new EmvData(new byte[8], 1, new byte[33]);
        return new AuthorizationCommand(
                UUID.randomUUID(),
                MerchantId.of("MERCH0000999"),
                TerminalId.of("TERM0042"),
                BIN6,
                PanHash.of("a".repeat(64)),
                Money.of("6000.00", INR),
                PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP,
                SystemTraceAuditNumber.of("000042"),
                emvData,
                new byte[]{0x03, 0x04},
                null,   // ksn: null (EMV without PIN in test)
                "05",
                "1111", // cardLast4
                null,   // cavv: null for non-3DS flows
                null    // eci:  null for non-3DS flows
        );
    }

    private MerchantProfile merchantProfile(MerchantProfile.Status status, String limit) {
        return new MerchantProfile(
                MerchantId.of("MERCH0000999"),
                "Test Merchant",
                "5411",
                status,
                Money.of(limit, INR),
                Money.of("500000.00", INR),
                new BigDecimal("1.50"),
                new BigDecimal("5.00"),
                "https://merchant.example.com/webhook",
                "secret123",
                null
        );
    }

    private void stubBinFound() {
        when(binLookupPort.lookup(BIN6)).thenReturn(Optional.of(new BinInfo(
                BIN6, PaymentNetwork.VISA, "HDFC Bank", "CREDIT", "CLASSIC", "IN", "HDFC", false)));
    }

    private void stubIdempotencyAcquired() {
        when(idempotencyPort.acquire(anyString(), any(Duration.class))).thenReturn(true);
    }

    private void stubActiveTerminal() {
        when(terminalRepository.findById(any())).thenReturn(Optional.of(
                new Terminal(TerminalId.of("TERM0042"), MerchantId.of("MERCH0000999"),
                             Terminal.Status.ACTIVE)));
    }

    private void stubActiveMerchant() {
        when(merchantRepository.findById(any())).thenReturn(Optional.of(
                merchantProfile(MerchantProfile.Status.ACTIVE, "100000.00")));
    }

    private void stubTransactionSave() {
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubNoFraud() {
        when(fraudScoringPort.score(any(), any())).thenReturn(Optional.empty());
    }
}
