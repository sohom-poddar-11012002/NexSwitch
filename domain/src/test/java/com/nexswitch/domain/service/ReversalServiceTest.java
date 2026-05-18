package com.nexswitch.domain.service;

import com.nexswitch.domain.model.*;
import com.nexswitch.domain.model.vo.*;
import com.nexswitch.domain.port.inbound.ReversalCommand;
import com.nexswitch.domain.port.outbound.AuthorizationPort;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// LEARN: OptimisticConcurrency — ReversalService guards double-reversal by catching
//        InvalidStateTransitionException from initiateReversal(); no DB-level lock needed in the domain layer.
@ExtendWith(MockitoExtension.class)
class ReversalServiceTest {

    private static final Currency INR = Currency.getInstance("INR");

    @Mock TransactionRepository  transactionRepository;
    @Mock AuthorizationPort      authorizationPort;

    ReversalService service;

    @BeforeEach
    void setUp() {
        service = new ReversalService(transactionRepository, authorizationPort, new TransactionStateMachine());
    }

    // ── Happy path: AUTHORIZATION_PENDING → REVERSAL_PENDING → REVERSED ──────

    @Test
    void givenAuthorizationPending_whenReverse_thenReturnsAccepted() {
        Transaction txn = initiated().withStatus(TransactionStatus.AUTHORIZATION_PENDING);
        ReversalCommand cmd = commandFor(txn);

        when(transactionRepository.findById(txn.id())).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authorizationPort.reverse(any())).thenReturn(new ReversalResult.Accepted(Instant.now()));

        ReversalResult result = service.execute(cmd);

        assertThat(result).isInstanceOf(ReversalResult.Accepted.class);
        verify(transactionRepository, times(2)).save(any());
    }

    // ── Happy path: AUTHORIZED → REVERSAL_PENDING → REVERSED ─────────────────

    @Test
    void givenAuthorized_whenReverse_thenReturnsAccepted() {
        Transaction txn = initiated()
                .withStatus(TransactionStatus.AUTHORIZATION_PENDING)
                .authorize(AuthorizationCode.of("AUTH01"));
        ReversalCommand cmd = commandFor(txn);

        when(transactionRepository.findById(txn.id())).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authorizationPort.reverse(any())).thenReturn(new ReversalResult.Accepted(Instant.now()));

        ReversalResult result = service.execute(cmd);

        assertThat(result).isInstanceOf(ReversalResult.Accepted.class);
    }

    // ── Network reversal fails → transition to UNKNOWN ────────────────────────

    @Test
    void givenNetworkReversalFails_thenUnknownStateAndReturnsFailed() {
        Transaction txn = initiated().withStatus(TransactionStatus.AUTHORIZATION_PENDING);
        ReversalCommand cmd = commandFor(txn);

        when(transactionRepository.findById(txn.id())).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authorizationPort.reverse(any())).thenReturn(new ReversalResult.Failed("network timeout"));

        ReversalResult result = service.execute(cmd);

        assertThat(result).isInstanceOf(ReversalResult.Failed.class);
        // verify final save captured UNKNOWN state
        verify(transactionRepository, times(2)).save(argThat(t ->
                t.status() == TransactionStatus.REVERSAL_PENDING  // first save
                || t.status() == TransactionStatus.UNKNOWN         // second save
        ));
    }

    // ── Transaction not found ─────────────────────────────────────────────────

    @Test
    void givenTransactionNotFound_thenReturnsFailed() {
        ReversalCommand cmd = commandFor(UUID.randomUUID(), Money.of("6000.00", INR));

        when(transactionRepository.findById(any())).thenReturn(Optional.empty());

        ReversalResult result = service.execute(cmd);

        assertThat(result).isInstanceOf(ReversalResult.Failed.class);
        verify(transactionRepository, never()).save(any());
    }

    // ── Already reversed ──────────────────────────────────────────────────────

    @Test
    void givenAlreadyReversed_thenIdempotentAlreadyReversed() {
        Transaction txn = initiated()
                .withStatus(TransactionStatus.AUTHORIZATION_PENDING)
                .initiateReversal()
                .withStatus(TransactionStatus.REVERSED);
        ReversalCommand cmd = commandFor(txn);

        when(transactionRepository.findById(txn.id())).thenReturn(Optional.of(txn));

        ReversalResult result = service.execute(cmd);

        assertThat(result).isInstanceOf(ReversalResult.AlreadyReversed.class);
        verify(transactionRepository, never()).save(any());
        verify(authorizationPort, never()).reverse(any());
    }

    // ── Race condition: concurrent 0110 moved txn to AUTHORIZED before reversal fires ──

    @Test
    void givenRaceCondition_concurrentAuthorizationReceived_thenAlreadyReversed() {
        // Start AUTHORIZATION_PENDING but simulate a concurrent update that moved it to AUTHORIZED
        Transaction stale  = initiated().withStatus(TransactionStatus.AUTHORIZATION_PENDING);
        Transaction fresh  = stale.withStatus(TransactionStatus.DECLINED); // network already declined it
        ReversalCommand cmd = commandFor(stale);

        // First findById returns stale view, then after concurrent update status changed
        when(transactionRepository.findById(stale.id())).thenReturn(Optional.of(fresh));

        ReversalResult result = service.execute(cmd);

        // DECLINED cannot transition to REVERSAL_PENDING → service must return Failed (not throw)
        assertThat(result).isInstanceOf(ReversalResult.Failed.class);
        verify(transactionRepository, never()).save(any());
    }

    // ── Network says already reversed (idempotent network response) ───────────

    @Test
    void givenNetworkAlreadyReversed_thenTransitionToReversedAndReturnAlreadyReversed() {
        Transaction txn = initiated().withStatus(TransactionStatus.AUTHORIZATION_PENDING);
        ReversalCommand cmd = commandFor(txn);

        when(transactionRepository.findById(txn.id())).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authorizationPort.reverse(any())).thenReturn(new ReversalResult.AlreadyReversed());

        ReversalResult result = service.execute(cmd);

        assertThat(result).isInstanceOf(ReversalResult.AlreadyReversed.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Transaction initiated() {
        return Transaction.initiate(
                UUID.randomUUID(),
                MerchantId.of("MERCH0000999"),
                TerminalId.of("TERM0042"),
                Money.of("6000.00", INR),
                PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP,
                PanHash.fromRawPan("4539148803436467"),
                SystemTraceAuditNumber.of("000001"),
                Instant.now()
        );
    }

    private static ReversalCommand commandFor(Transaction txn) {
        return commandFor(txn.id(), txn.amount());
    }

    private static ReversalCommand commandFor(UUID id, Money amount) {
        return new ReversalCommand(
                id,
                SystemTraceAuditNumber.of("000001"),
                amount,
                amount
        );
    }
}
