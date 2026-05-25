package com.nexswitch.adapters.outbound.persistence;

import com.nexswitch.adapters.outbound.persistence.entity.CollectRequestEntity;
import com.nexswitch.adapters.outbound.persistence.jpa.JpaCollectRequestRepository;
import com.nexswitch.adapters.outbound.persistence.mapper.CollectRequestMapper;
import com.nexswitch.domain.model.CollectRequest;
import com.nexswitch.domain.port.outbound.CollectRequestPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class PostgresCollectRequestRepository implements CollectRequestPort {

    private final JpaCollectRequestRepository jpa;
    private final CollectRequestMapper mapper;

    public PostgresCollectRequestRepository(JpaCollectRequestRepository jpa,
                                            CollectRequestMapper mapper) {
        this.jpa    = jpa;
        this.mapper = mapper;
    }

    @Override
    public void save(CollectRequest request) {
        jpa.save(mapper.toEntity(request));
    }

    @Override
    public Optional<CollectRequest> findByCollectId(String collectId) {
        return jpa.findByCollectId(collectId).map(mapper::toDomain);
    }

    @Override
    // LEARN: @Transactional wraps findByCollectId + save in a single DB transaction —
    //        without it, concurrent UPI Collect outcome POSTs on the same collectId can
    //        interleave: both reads succeed, one write is silently overwritten.
    @Transactional
    public void update(CollectRequest request) {
        CollectRequestEntity entity = jpa.findByCollectId(request.collectId().value())
                .orElseThrow(() -> new IllegalArgumentException("CollectRequest not found: " + request.collectId()));
        entity.setStatus(request.status().name());
        entity.setNpciTxnId(request.npciTxnId() != null ? request.npciTxnId().value() : null);
        jpa.save(entity);
    }
}
