package com.nexswitch.adapters.outbound.persistence.jpa;

import com.nexswitch.adapters.outbound.persistence.entity.MerchantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// LEARN: SpringDataJpa — derived query findByStatus() generates SQL at startup; no runtime string parsing
public interface JpaMerchantRepository extends JpaRepository<MerchantEntity, String> {

    List<MerchantEntity> findByStatus(String status);
}
