package com.nexswitch.qa.domain.model;

public enum ExecutionStatus {
    PENDING,
    RUNNING,
    WAITING_FOR_HUMAN,
    PASSED,
    FAILED,
    TIMED_OUT,
    ABORTED
}
