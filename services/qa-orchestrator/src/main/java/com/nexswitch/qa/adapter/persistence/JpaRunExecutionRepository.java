package com.nexswitch.qa.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

// LEARN: JPQL LIMIT — Spring Data JPA 3.x (Hibernate 6+) supports LIMIT directly in JPQL,
//        avoiding the need for Pageable just to fetch the top-N rows.
public interface JpaRunExecutionRepository extends JpaRepository<RunExecutionEntity, UUID> {

    @Query("SELECT e FROM RunExecutionEntity e ORDER BY e.startedAt DESC LIMIT :limit")
    List<RunExecutionEntity> findRecentExecutions(int limit);
}
