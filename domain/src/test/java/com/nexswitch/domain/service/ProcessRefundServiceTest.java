package com.nexswitch.domain.service;

import com.nexswitch.domain.fixture.TransactionFixture;
import com.nexswitch.domain.model.*;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.port.inbound.RefundCommand;
import com.nexswitch.domain.port.outbound.RefundPort;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessRefundServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock RefundPort refundPort;
    @InjectMocks ProcessRefundService service;

    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void returnsFailedWhenTransactionNotFound() {
        when(transactionRepository.findById(any())).thenReturn(Optional.empty());
        UUID id = UUID.randomUUID();
        RefundCommand cmd = new RefundCommand(id, Money.of("500.00", INR), "duplicate");

        RefundResult result = service.execute(cmd);

        assertThat(result).isInstanceOf(RefundResult.Failed.class);
        verifyNoInteractions(refundPort);
    }

    @Test
    void returnsFailedWhenStatusNotRefundable() {
        Transaction txn = TransactionFixture.withStatus(TransactionStatus.INITIATED);
        when(transactionRepository.findById(txn.id())).thenReturn(Optional.of(txn));
        RefundCommand cmd = new RefundCommand(txn.id(), Money.of("500.00", INR), "user request");

        RefundResult result = service.execute(cmd);

        assertThat(result).isInstanceOf(RefundResult.Failed.class);
        verifyNoInteractions(refundPort);
    }

    @Test
    void refundSucceedsAndTransitionsToRefundInitiated() {
        Transaction txn = TransactionFixture.withStatus(TransactionStatus.AUTHORIZED);
        when(transactionRepository.findById(txn.id())).thenReturn(Optional.of(txn));
        RefundResult.Initiated initiated = new RefundResult.Initiated(UUID.randomUUID(), Instant.now());
        when(refundPort.requestRefund(any(), any())).thenReturn(initiated);

        RefundCommand cmd = new RefundCommand(txn.id(), Money.of("500.00", INR), "user request");
        RefundResult result = service.execute(cmd);

        assertThat(result).isEqualTo(initiated);
        verify(transactionRepository).save(argThat(t -> t.status() == TransactionStatus.REFUND_INITIATED));
    }

    @Test
    void returnsFailedWhenRefundAmountExceedsOriginal() {
        Transaction txn = TransactionFixture.withStatus(TransactionStatus.AUTHORIZED);
        when(transactionRepository.findById(txn.id())).thenReturn(Optional.of(txn));
        // original amount is 6000.00; try to refund 9999
        RefundCommand cmd = new RefundCommand(txn.id(), Money.of("9999.00", INR), "over-refund");

        RefundResult result = service.execute(cmd);

        assertThat(result).isInstanceOf(RefundResult.Failed.class);
        assertThat(((RefundResult.Failed) result).reason()).contains("exceeds");
        verifyNoInteractions(refundPort);
    }
}
