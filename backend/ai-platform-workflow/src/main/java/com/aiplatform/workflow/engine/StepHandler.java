package com.aiplatform.workflow.engine;

import com.aiplatform.workflow.model.WorkflowSpec;

import java.util.Map;

/**
 * One step executor. Implementations are looked up by the step {@code type}
 * string in the workflow spec.
 */
public interface StepHandler {

    /** The step type this handler is registered for. */
    String type();

    /**
     * Execute the step. Implementations may mutate {@code context} to
     * pass data downstream.
     */
    void execute(WorkflowSpec.Step step, Map<String, Object> context, StepLog log) throws Exception;
}
