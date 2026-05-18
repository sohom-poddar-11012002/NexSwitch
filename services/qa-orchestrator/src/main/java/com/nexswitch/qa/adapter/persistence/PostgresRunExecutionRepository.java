package com.nexswitch.qa.adapter.persistence;

import com.nexswitch.qa.domain.model.ExecutionStatus;
import com.nexswitch.qa.domain.model.RunExecution;
import com.nexswitch.qa.domain.port.outbound.RunExecutionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PostgresRunExecutionRepository implements RunExecutionRepository {

    private final JpaRunExecutionRepository jpa;

    public PostgresRunExecutionRepository(JpaRunExecutionRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RunExecution save(RunExecution execution) {
        RunExecutionEntity entity = jpa.findById(execution.id())
                .orElse(new RunExecutionEntity(
                        execution.id(),
                        execution.runId(),
                        execution.status().name(),
                        Map.copyOf(execution.sharedContext()),
                        execution.startedAt(),
                        execution.completedAt()));
        entity.setStatus(execution.status().name());
        entity.setCompletedAt(execution.completedAt());
        jpa.save(entity);
        return execution;
    }

    @Override
    public Optional<RunExecution> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<RunExecution> findRecentExecutions(int limit) {
        return jpa.findRecentExecutions(limit).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private RunExecution toDomain(RunExecutionEntity e) {
        return new RunExecution(
                e.getId(),
                e.getRunId(),
                ExecutionStatus.valueOf(e.getStatus()),
                List.of(),  // step executions not stored as entities — fetched from SSE events / response
                e.getRunVariables() != null ? e.getRunVariables() : Map.of(),
                e.getStartedAt(),
                e.getCompletedAt());
    }
}
