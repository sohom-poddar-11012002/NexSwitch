package com.payments.adapters.outbound.persistence.jpa;

import com.payments.adapters.outbound.persistence.entity.TerminalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// LEARN: SpringDataJpa — @Query for JOIN-heavy queries; derived method names for simple lookups
public interface JpaTerminalRepository extends JpaRepository<TerminalEntity, String> {

    List<TerminalEntity> findByMerchantId(String merchantId);

    List<TerminalEntity> findByMerchantIdAndStatus(String merchantId, String status);
}
