package com.aiplatform.workflow.model;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Runtime state of a workflow run. The {@link #context} carries the
 * step-to-step data flow; the engine updates it as each step completes.
 */
@Data
public class WorkflowRun {

    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }

    private String runId;
    private String workflowId;
    private String workflowName;
    private Status status = Status.QUEUED;
    private int progress;
    private String currentStep;
    private Map<String, Object> context = new java.util.HashMap<>();
    private String error;
    private Instant startedAt = Instant.now();
    private Instant finishedAt;
}
