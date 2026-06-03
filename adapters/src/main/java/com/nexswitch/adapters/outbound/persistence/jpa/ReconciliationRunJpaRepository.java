package com.nexswitch.adapters.outbound.persistence.jpa;

import com.nexswitch.adapters.outbound.persistence.entity.ReconciliationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationRunJpaRepository extends JpaRepository<ReconciliationRunEntity, UUID> {
}
