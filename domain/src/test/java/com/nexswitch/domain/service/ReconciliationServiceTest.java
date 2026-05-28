package com.nexswitch.domain.service;

import com.nexswitch.domain.fixture.TransactionFixture;
import com.nexswitch.domain.model.*;
import com.nexswitch.domain.port.inbound.ReconciliationCommand;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock TransactionRepository transactionRepository;

    ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(transactionRepository);
    }

    @Test
    void reconcilesPendingTransactionsForMatchingNetwork() {
        Transaction txn = TransactionFixture.withStatus(TransactionStatus.SETTLEMENT_PENDING);
        when(transactionRepository.findByStatus(TransactionStatus.SETTLEMENT_PENDING))
                .thenReturn(List.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationCommand cmd = new ReconciliationCommand(
                LocalDate.now().minusDays(1), Set.of(PaymentNetwork.VISA));
        ReconciliationResult result = service.execute(cmd);

        assertThat(result).isInstanceOf(ReconciliationResult.Completed.class);
        ReconciliationResult.Completed completed = (ReconciliationResult.Completed) result;
        assertThat(completed.matchedCount()).isEqualTo(1);
        assertThat(completed.mismatchCount()).isEqualTo(0);
        verify(transactionRepository).save(argThat(t -> t.status() == TransactionStatus.RECONCILED));
    }

    @Test
    void countsAssMismatchWhenNetworkNotInScope() {
        Transaction txn = TransactionFixture.withStatus(TransactionStatus.SETTLEMENT_PENDING);
        when(transactionRepository.findByStatus(TransactionStatus.SETTLEMENT_PENDING))
                .thenReturn(List.of(txn));

        ReconciliationCommand cmd = new ReconciliationCommand(
                LocalDate.now().minusDays(1), Set.of(PaymentNetwork.RUPAY));
        ReconciliationResult result = service.execute(cmd);

        assertThat(result).isInstanceOf(ReconciliationResult.Completed.class);
        ReconciliationResult.Completed completed = (ReconciliationResult.Completed) result;
        assertThat(completed.matchedCount()).isEqualTo(0);
        assertThat(completed.mismatchCount()).isEqualTo(1);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void returnsCompletedWithZerosWhenNoPendingTransactions() {
        when(transactionRepository.findByStatus(TransactionStatus.SETTLEMENT_PENDING))
                .thenReturn(List.of());

        ReconciliationCommand cmd = new ReconciliationCommand(
                LocalDate.now().minusDays(1), Set.of(PaymentNetwork.VISA));
        ReconciliationResult result = service.execute(cmd);

        assertThat(result).isInstanceOf(ReconciliationResult.Completed.class);
        ReconciliationResult.Completed completed = (ReconciliationResult.Completed) result;
        assertThat(completed.matchedCount()).isEqualTo(0);
        assertThat(completed.mismatchCount()).isEqualTo(0);
    }
}
