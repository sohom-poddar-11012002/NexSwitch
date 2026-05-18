package com.nexswitch.qa.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface JpaRunExecutionRepository extends JpaRepository<RunExecutionEntity, UUID> {

    @Query("SELECT e FROM RunExecutionEntity e ORDER BY e.startedAt DESC LIMIT :limit")
    List<RunExecutionEntity> findRecentExecutions(int limit);
}
