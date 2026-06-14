package com.aiplatform.workflow.engine;

import com.aiplatform.workflow.model.WorkflowSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class SleepStepHandler implements StepHandler {
    @Override public String type() { return "sleep"; }
    @Override
    public void execute(WorkflowSpec.Step step, Map<String, Object> context, StepLog slog) throws InterruptedException {
        long ms = ((Number) step.getParams().getOrDefault("ms", 1000)).longValue();
        slog.info("sleeping " + ms + "ms");
        Thread.sleep(ms);
    }
}
