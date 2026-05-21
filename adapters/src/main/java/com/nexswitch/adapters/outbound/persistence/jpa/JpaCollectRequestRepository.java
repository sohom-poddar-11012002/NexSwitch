package com.nexswitch.adapters.outbound.persistence.jpa;

import com.nexswitch.adapters.outbound.persistence.entity.CollectRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaCollectRequestRepository extends JpaRepository<CollectRequestEntity, String> {

    Optional<CollectRequestEntity> findByCollectId(String collectId);
}
