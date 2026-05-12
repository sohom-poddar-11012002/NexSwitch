package com.payments.domain.port.outbound;

import com.payments.domain.model.Transaction;
import com.payments.domain.model.TransactionStatus;
import com.payments.domain.model.vo.AcquirerReferenceNumber;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(UUID id);
    Optional<Transaction> findByArn(AcquirerReferenceNumber arn);
    List<Transaction> findByStatus(TransactionStatus status);
    List<Transaction> findCapturedSince(Instant since);
}
