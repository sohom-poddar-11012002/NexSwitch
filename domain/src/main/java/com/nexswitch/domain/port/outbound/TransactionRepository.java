package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.model.vo.AcquirerReferenceNumber;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// LEARN: RepositoryPort — domain defines the interface; JPA @Entity is invisible to domain
public interface TransactionRepository {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(UUID id);
    Optional<Transaction> findByArn(AcquirerReferenceNumber arn);
    List<Transaction> findByStatus(TransactionStatus status);
    List<Transaction> findCapturedSince(Instant since);
    // LEARN: DerivedQuery — callers pass a threshold Instant; the port stays pure, no Duration logic here
    List<Transaction> findAuthorizationPendingOlderThan(Instant threshold);
}
