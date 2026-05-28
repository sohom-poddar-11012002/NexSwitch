package com.nexswitch.adapters.outbound.persistence;

import com.nexswitch.adapters.outbound.persistence.jpa.JpaTransactionRepository;
import com.nexswitch.adapters.outbound.persistence.mapper.TransactionMapper;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.model.event.DomainEvent;
import com.nexswitch.domain.model.vo.AcquirerReferenceNumber;
import com.nexswitch.domain.port.outbound.DomainEventPublisherPort;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// LEARN: AdapterPattern — implements domain port; domain calls save(Transaction), never touches @Entity
@Repository
public class PostgresTransactionRepository implements TransactionRepository {

    private final JpaTransactionRepository jpa;
    private final TransactionMapper mapper;
    private final DomainEventPublisherPort eventPublisher;

    public PostgresTransactionRepository(JpaTransactionRepository jpa,
                                          TransactionMapper mapper,
                                          DomainEventPublisherPort eventPublisher) {
        this.jpa = jpa;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Transaction save(Transaction transaction) {
        // LEARN: pullDomainEvents() clears the event list on the aggregate — must be called
        //        before toEntity() so events are not lost when the builder copies state.
        List<DomainEvent<?>> events = transaction.pullDomainEvents();
        Transaction saved = mapper.toDomain(jpa.save(mapper.toEntity(transaction)));
        events.forEach(eventPublisher::publish);
        return saved;
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Transaction> findByArn(AcquirerReferenceNumber arn) {
        return jpa.findByArn(arn.value()).map(mapper::toDomain);
    }

    @Override
    public List<Transaction> findByStatus(TransactionStatus status) {
        return jpa.findByStatus(status.name()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findCapturedSince(Instant since) {
        return jpa.findCapturedSince(since).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findAuthorizationPendingOlderThan(Instant threshold) {
        return jpa.findAuthorizationPendingOlderThan(threshold).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
