package com.nexswitch.adapters.outbound.persistence.jpa;

import com.nexswitch.adapters.outbound.persistence.entity.ReconciliationExceptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationExceptionJpaRepository extends JpaRepository<ReconciliationExceptionEntity, UUID> {
}
