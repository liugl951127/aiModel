package com.aiplatform.workflow.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.workflow.engine.WorkflowEngine;
import com.aiplatform.workflow.model.WorkflowRun;
import com.aiplatform.workflow.model.WorkflowSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Workflow orchestration REST surface.
 *
 * <ul>
 *   <li>{@code POST /api/workflow/run} - submit a workflow spec, returns a runId</li>
 *   <li>{@code GET  /api/workflow/run/{id}} - poll a run</li>
 *   <li>{@code GET  /api/workflow/runs} - list runs</li>
 *   <li>{@code GET  /api/workflow/templates} - canned train→eval→deploy pipeline</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEngine engine;

    @PostMapping("/run")
    public Result<String> run(@RequestBody WorkflowSpec spec) {
        String runId = "wf-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        log.info("[WF] submit id={} name={} steps={}",
                runId, spec.getName(), spec.getSteps() == null ? 0 : spec.getSteps().size());
        // Execute async so the client doesn't block
        runAsync(spec);
        return Result.success(runId);
    }

    @Async
    public void runAsync(WorkflowSpec spec) {
        try {
            engine.run(spec);
        } catch (Throwable t) {
            log.error("[WF] async run failed: {}", t.getMessage(), t);
        }
    }

    @GetMapping("/run/{id}")
    public Result<WorkflowRun> get(@PathVariable String id) {
        WorkflowRun r = engine.get(id);
        if (r == null) return Result.fail(404, "run not found: " + id);
        return Result.success(r);
    }

    @GetMapping("/runs")
    public Result<Map<String, WorkflowRun>> list() {
        return Result.success(engine.all());
    }

    /** Pre-baked train → evaluate → deploy template. */
    @GetMapping("/templates/train-eval-deploy")
    public Result<WorkflowSpec> template() {
        WorkflowSpec t = new WorkflowSpec();
        t.setId("train-eval-deploy");
        t.setName("Train → Evaluate → Deploy");
        t.setDescription("Standard pipeline: train on a corpus, evaluate the bundle, then deploy to inference.");
        t.setSteps(java.util.List.of(
                new WorkflowSpec.Step("train", "train",
                        java.util.List.of(),
                        java.util.Map.of("maxIters", 200, "nEmbd", 128, "nLayer", 4, "nHead", 4, "blockSize", 64, "batchSize", 16)),
                new WorkflowSpec.Step("evaluate", "evaluate",
                        java.util.List.of("train"),
                        java.util.Map.of()),
                new WorkflowSpec.Step("deploy", "deploy",
                        java.util.List.of("evaluate"),
                        java.util.Map.of()),
                new WorkflowSpec.Step("notify", "notify",
                        java.util.List.of("deploy"),
                        java.util.Map.of("message", "deployment complete"))
        ));
        return Result.success(t);
    }
}
