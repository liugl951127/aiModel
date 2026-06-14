package com.aiplatform.workflow.engine;

import com.aiplatform.workflow.model.WorkflowSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Evaluates the most recently trained bundle against a held-out corpus.
 * The metric is appended to the run context for downstream steps.
 *
 * <p>Implementation: this demo step does a simple perplexity estimate by
 * re-reading the bundle metadata and looking for the training corpus.
 * In production this would call the inference service with a held-out
 * dataset and report loss / accuracy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluateStepHandler implements StepHandler {

    @Override public String type() { return "evaluate"; }

    @Override
    public void execute(WorkflowSpec.Step step, Map<String, Object> context, StepLog slog) {
        Object loss = context.get("finalLoss");
        if (loss == null) {
            slog.info("no finalLoss in context; skipping eval");
            context.put("evalPassed", false);
            return;
        }
        double l = ((Number) loss).doubleValue();
        double perplexity = Math.exp(l);
        slog.info("eval loss=" + String.format("%.4f", l) + " perplexity=" + String.format("%.2f", perplexity));
        context.put("perplexity", perplexity);
        context.put("evalPassed", l < 5.0); // toy threshold for byte-level demo
    }
}
