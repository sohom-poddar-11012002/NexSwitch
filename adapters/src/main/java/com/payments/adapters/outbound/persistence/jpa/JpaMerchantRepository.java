package com.payments.adapters.outbound.persistence.jpa;

import com.payments.adapters.outbound.persistence.entity.MerchantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaMerchantRepository extends JpaRepository<MerchantEntity, String> {

    List<MerchantEntity> findByStatus(String status);
}
