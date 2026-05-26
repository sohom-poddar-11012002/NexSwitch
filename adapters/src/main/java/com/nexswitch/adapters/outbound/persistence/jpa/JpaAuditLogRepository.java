package com.nexswitch.adapters.outbound.persistence.jpa;

import com.nexswitch.adapters.outbound.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaAuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
}
