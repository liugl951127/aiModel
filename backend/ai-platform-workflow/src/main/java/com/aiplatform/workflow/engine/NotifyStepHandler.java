package com.aiplatform.workflow.engine;

import com.aiplatform.workflow.model.WorkflowSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * No-op step used to mark a checkpoint in the workflow run. Logs the
 * current context snapshot — useful for debugging.
 */
@Slf4j
@Component
public class NotifyStepHandler implements StepHandler {
    @Override public String type() { return "notify"; }
    @Override
    public void execute(WorkflowSpec.Step step, Map<String, Object> context, StepLog slog) {
        String message = (String) step.getParams().getOrDefault("message", step.getName());
        slog.info("NOTIFY: " + message);
        slog.info("context: " + context);
    }
}
