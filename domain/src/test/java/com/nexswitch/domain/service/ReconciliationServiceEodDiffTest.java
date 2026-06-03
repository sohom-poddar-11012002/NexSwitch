package com.nexswitch.domain.service;

import com.nexswitch.domain.fixture.TransactionFixture;
import com.nexswitch.domain.model.*;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.port.inbound.ReconciliationCommand;
import com.nexswitch.domain.port.outbound.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// LEARN: Three-way reconciliation diff — switch internal records vs. network EOD file.
//        Each test covers one distinct category of exception that the service must raise.
@ExtendWith(MockitoExtension.class)
class ReconciliationServiceEodDiffTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 6, 3);

    @Mock TransactionRepository transactionRepository;
    @Mock AuditPort auditPort;
    @Mock EodFilePort eodFilePort;
    @Mock ReconciliationExceptionPort exceptionPort;
    @Mock ReconciliationRunPort runPort;

    ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(
                transactionRepository,
                new TransactionStateMachine(),
                auditPort,
                eodFilePort,
                exceptionPort,
                runPort);
        when(runPort.startRun(any())).thenReturn(UUID.randomUUID());
    }

    @Test
    void cleanMatch_reconciles_transaction_and_writes_no_exception() {
        Transaction txn = TransactionFixture.withStatus(TransactionStatus.SETTLEMENT_PENDING);
        EodFilePort.SettlementRecord record = eodRecord(txn.id(), txn.amount());

        when(transactionRepository.findByStatus(TransactionStatus.SETTLEMENT_PENDING))
                .thenReturn(List.of(txn));
        when(eodFilePort.fetchForDate(RUN_DATE, PaymentNetwork.VISA)).thenReturn(List.of(record));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationResult result = service.execute(cmd(PaymentNetwork.VISA));

        assertThat(result).isInstanceOf(ReconciliationResult.Completed.class);
        ReconciliationResult.Completed c = (ReconciliationResult.Completed) result;
        assertThat(c.matchedCount()).isEqualTo(1);
        assertThat(c.mismatchCount()).isEqualTo(0);
        verify(transactionRepository).save(argThat(t -> t.status() == TransactionStatus.RECONCILED));
        verify(exceptionPort, never()).save(any());
    }

    @Test
    void missingInNetwork_writes_HIGH_exception_and_counts_as_mismatch() {
        Transaction txn = TransactionFixture.withStatus(TransactionStatus.SETTLEMENT_PENDING);

        when(transactionRepository.findByStatus(TransactionStatus.SETTLEMENT_PENDING))
                .thenReturn(List.of(txn));
        when(eodFilePort.fetchForDate(RUN_DATE, PaymentNetwork.VISA)).thenReturn(List.of()); // not in file

        ReconciliationResult result = service.execute(cmd(PaymentNetwork.VISA));

        ReconciliationResult.Completed c = (ReconciliationResult.Completed) result;
        assertThat(c.matchedCount()).isEqualTo(0);
        assertThat(c.mismatchCount()).isEqualTo(1);
        verify(transactionRepository, never()).save(any());

        ArgumentCaptor<ReconciliationException> captor = ArgumentCaptor.forClass(ReconciliationException.class);
        verify(exceptionPort).save(captor.capture());
        ReconciliationException ex = captor.getValue();
        assertThat(ex.category()).isEqualTo(ReconciliationException.ExceptionCategory.MISSING_IN_NETWORK);
        assertThat(ex.severity()).isEqualTo(ReconciliationException.Severity.HIGH);
        assertThat(ex.transactionId()).isEqualTo(txn.id());
        assertThat(ex.ourAmount()).isEqualByComparingTo(txn.amount().amount());
        assertThat(ex.networkAmount()).isNull();
    }

    @Test
    void missingInSwitch_writes_CRIT_exception_and_counts_as_mismatch() {
        UUID foreignId = UUID.randomUUID();
        EodFilePort.SettlementRecord orphanRecord = eodRecord(foreignId, Money.of("1000.00", INR));

        when(transactionRepository.findByStatus(TransactionStatus.SETTLEMENT_PENDING))
                .thenReturn(List.of()); // nothing in our DB
        when(eodFilePort.fetchForDate(RUN_DATE, PaymentNetwork.VISA)).thenReturn(List.of(orphanRecord));

        ReconciliationResult result = service.execute(cmd(PaymentNetwork.VISA));

        ReconciliationResult.Completed c = (ReconciliationResult.Completed) result;
        assertThat(c.matchedCount()).isEqualTo(0);
        assertThat(c.mismatchCount()).isEqualTo(1);

        ArgumentCaptor<ReconciliationException> captor = ArgumentCaptor.forClass(ReconciliationException.class);
        verify(exceptionPort).save(captor.capture());
        ReconciliationException ex = captor.getValue();
        assertThat(ex.category()).isEqualTo(ReconciliationException.ExceptionCategory.MISSING_IN_SWITCH);
        assertThat(ex.severity()).isEqualTo(ReconciliationException.Severity.CRIT);
        assertThat(ex.transactionId()).isNull();
        assertThat(ex.ourAmount()).isNull();
        assertThat(ex.networkAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void amountMismatch_writes_CRIT_exception_and_counts_as_mismatch() {
        Transaction txn = TransactionFixture.withStatus(TransactionStatus.SETTLEMENT_PENDING);
        Money wrongAmount = Money.of("9999.00", INR); // different from txn.amount() = 6000.00
        EodFilePort.SettlementRecord record = eodRecord(txn.id(), wrongAmount);

        when(transactionRepository.findByStatus(TransactionStatus.SETTLEMENT_PENDING))
                .thenReturn(List.of(txn));
        when(eodFilePort.fetchForDate(RUN_DATE, PaymentNetwork.VISA)).thenReturn(List.of(record));

        ReconciliationResult result = service.execute(cmd(PaymentNetwork.VISA));

        ReconciliationResult.Completed c = (ReconciliationResult.Completed) result;
        assertThat(c.matchedCount()).isEqualTo(0);
        assertThat(c.mismatchCount()).isEqualTo(1);
        verify(transactionRepository, never()).save(any());

        ArgumentCaptor<ReconciliationException> captor = ArgumentCaptor.forClass(ReconciliationException.class);
        verify(exceptionPort).save(captor.capture());
        ReconciliationException ex = captor.getValue();
        assertThat(ex.category()).isEqualTo(ReconciliationException.ExceptionCategory.AMOUNT_MISMATCH);
        assertThat(ex.severity()).isEqualTo(ReconciliationException.Severity.CRIT);
        assertThat(ex.transactionId()).isEqualTo(txn.id());
        assertThat(ex.ourAmount()).isEqualByComparingTo(new BigDecimal("6000.00"));
        assertThat(ex.networkAmount()).isEqualByComparingTo(new BigDecimal("9999.00"));
    }

    @Test
    void runPort_startRun_and_completeRun_called_with_correct_counts() {
        Transaction txn = TransactionFixture.withStatus(TransactionStatus.SETTLEMENT_PENDING);
        EodFilePort.SettlementRecord record = eodRecord(txn.id(), txn.amount());

        UUID runId = UUID.randomUUID();
        when(runPort.startRun(RUN_DATE)).thenReturn(runId);
        when(transactionRepository.findByStatus(TransactionStatus.SETTLEMENT_PENDING))
                .thenReturn(List.of(txn));
        when(eodFilePort.fetchForDate(RUN_DATE, PaymentNetwork.VISA)).thenReturn(List.of(record));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.execute(cmd(PaymentNetwork.VISA));

        verify(runPort).startRun(RUN_DATE);
        verify(runPort).completeRun(runId, 1, 1, 0);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private ReconciliationCommand cmd(PaymentNetwork... networks) {
        return new ReconciliationCommand(RUN_DATE, Set.of(networks));
    }

    private EodFilePort.SettlementRecord eodRecord(UUID txnId, Money amount) {
        return new EodFilePort.SettlementRecord(txnId, "REF" + txnId.toString().substring(0, 8),
                amount, PaymentNetwork.VISA, "00");
    }
}
