package com.nexswitch.domain.service;

import com.nexswitch.domain.model.RefundResult;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.port.inbound.ProcessRefundUseCase;
import com.nexswitch.domain.port.inbound.RefundCommand;
import com.nexswitch.domain.port.outbound.RefundPort;
import com.nexswitch.domain.port.outbound.TransactionRepository;

import java.util.Optional;

// LEARN: DomainService — orchestrates two ports (TransactionRepository + RefundPort);
//        no Spring annotations; wired in AdapterConfig as a plain @Bean.
public class ProcessRefundService implements ProcessRefundUseCase {

    private final TransactionRepository transactionRepository;
    private final RefundPort refundPort;

    public ProcessRefundService(TransactionRepository transactionRepository, RefundPort refundPort) {
        this.transactionRepository = transactionRepository;
        this.refundPort = refundPort;
    }

    @Override
    public RefundResult execute(RefundCommand command) {
        Optional<Transaction> found = transactionRepository.findById(command.originalTransactionId());
        if (found.isEmpty()) {
            return new RefundResult.Failed("transaction not found: " + command.originalTransactionId());
        }
        Transaction txn = found.get();
        TransactionStatus status = txn.status();
        if (status != TransactionStatus.AUTHORIZED
                && status != TransactionStatus.CAPTURED
                && status != TransactionStatus.PAID_OUT) {
            return new RefundResult.Failed("transaction in non-refundable status: " + status);
        }
        if (command.refundAmount().amount().compareTo(txn.amount().amount()) > 0) {
            return new RefundResult.Failed("refund amount exceeds original transaction amount");
        }
        RefundResult result = refundPort.requestRefund(txn, command.refundAmount());
        if (result instanceof RefundResult.Initiated) {
            Transaction updated = txn.withStatus(TransactionStatus.REFUND_INITIATED);
            transactionRepository.save(updated);
        }
        return result;
    }
}
