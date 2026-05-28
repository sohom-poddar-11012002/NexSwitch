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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.context.ApplicationEventPublisher;

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
    private final ApplicationEventPublisher springEventPublisher;

    public PostgresTransactionRepository(JpaTransactionRepository jpa,
                                          TransactionMapper mapper,
                                          DomainEventPublisherPort eventPublisher,
                                          ApplicationEventPublisher springEventPublisher) {
        this.jpa                  = jpa;
        this.mapper               = mapper;
        this.eventPublisher       = eventPublisher;
        this.springEventPublisher = springEventPublisher;
    }

    @Override
    @Transactional
    public Transaction save(Transaction transaction) {
        // LEARN: pullDomainEvents() clears the event list on the aggregate — must be called
        //        before toEntity() so events are not lost when the builder copies state.
        List<DomainEvent<?>> events = transaction.pullDomainEvents();
        Transaction saved = mapper.toDomain(jpa.save(mapper.toEntity(transaction)));
        // LEARN: @TransactionalEventListener(AFTER_COMMIT) guarantees DB is committed BEFORE Kafka
        //        receives the event. Publishing inside the transaction risks Kafka seeing the event
        //        before DB writes are visible — a read-your-writes violation under high concurrency.
        //        We wrap events in a Spring application event so @TransactionalEventListener fires after commit.
        if (!events.isEmpty()) {
            springEventPublisher.publishEvent(new DomainEventsHolder(events));
        }
        return saved;
    }

    /** Spring application event wrapper — carries domain events to the after-commit listener. */
    public record DomainEventsHolder(List<DomainEvent<?>> events) {}

    /**
     * Fires AFTER the transaction commits — guarantees at-least-once Kafka delivery order:
     * DB committed → Kafka published → consumers read consistent state.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(DomainEventsHolder holder) {
        holder.events().forEach(eventPublisher::publish);
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
