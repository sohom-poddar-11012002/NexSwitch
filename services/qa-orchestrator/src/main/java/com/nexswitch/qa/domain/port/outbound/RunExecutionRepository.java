package com.nexswitch.qa.domain.port.outbound;

import com.nexswitch.qa.domain.model.RunExecution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RunExecutionRepository {

    RunExecution save(RunExecution execution);

    Optional<RunExecution> findById(UUID id);

    List<RunExecution> findRecentExecutions(int limit);
}
