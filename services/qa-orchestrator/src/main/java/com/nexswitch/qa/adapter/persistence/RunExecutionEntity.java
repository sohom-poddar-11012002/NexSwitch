package com.nexswitch.qa.adapter.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "qa_run_executions")
public class RunExecutionEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private String runId;

    @Column(nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "run_variables", columnDefinition = "jsonb")
    private Map<String, Object> runVariables;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected RunExecutionEntity() {}

    public RunExecutionEntity(UUID id, String runId, String status,
                              Map<String, Object> runVariables, Instant startedAt, Instant completedAt) {
        this.id           = id;
        this.runId        = runId;
        this.status       = status;
        this.runVariables = runVariables;
        this.startedAt    = startedAt;
        this.completedAt  = completedAt;
    }

    public UUID getId()                        { return id; }
    public String getRunId()                   { return runId; }
    public String getStatus()                  { return status; }
    public Map<String, Object> getRunVariables() { return runVariables; }
    public Instant getStartedAt()              { return startedAt; }
    public Instant getCompletedAt()            { return completedAt; }
    public void setStatus(String status)       { this.status = status; }
    public void setCompletedAt(Instant t)      { this.completedAt = t; }
}
