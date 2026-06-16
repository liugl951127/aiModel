package com.aiplatform.workflow.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.workflow.engine.WorkflowEngine;
import com.aiplatform.workflow.model.WorkflowRun;
import com.aiplatform.workflow.model.WorkflowSpec;
import com.aiplatform.workflow.service.WorkflowSpecRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Workflow orchestration REST surface.
 *
 * <ul>
 *   <li>CRUD 工作流定义 (前台 WorkflowList 用)</li>
 *   <li>{@code POST /api/workflow/run} - 提交 spec 异步执行</li>
 *   <li>{@code GET  /api/workflow/run/{id}} - 查 run</li>
 *   <li>{@code GET  /api/workflow/runs} - 查所有 run</li>
 *   <li>{@code GET  /api/workflow/templates/train-eval-deploy} - 模板</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEngine engine;
    private final WorkflowSpecRepository specRepo;

    // ============== CRUD ==============
    @PostMapping("/spec")
    public Result<WorkflowSpec> saveSpec(@RequestBody WorkflowSpec spec, HttpServletRequest req) {
        String user = req.getHeader("X-Username");
        if (user == null) user = "anonymous";
        WorkflowSpec saved = specRepo.save(spec);
        specRepo.setAuthor(saved.getId(), user);
        log.info("[WF] save id={} name={} author={}", saved.getId(), saved.getName(), user);
        return Result.success(saved);
    }

    @GetMapping("/spec/list")
    public Result<List<Map<String, Object>>> listSpecs() {
        return Result.success(specRepo.list());
    }

    @GetMapping("/spec/{id}")
    public Result<WorkflowSpec> getSpec(@PathVariable String id) {
        WorkflowSpec s = specRepo.get(id);
        if (s == null) return Result.fail(404, "workflow spec not found: " + id);
        return Result.success(s);
    }

    @DeleteMapping("/spec/{id}")
    public Result<Void> deleteSpec(@PathVariable String id) {
        specRepo.delete(id);
        return Result.success();
    }

    @PostMapping("/spec/{id}/duplicate")
    public Result<WorkflowSpec> duplicateSpec(@PathVariable String id) {
        WorkflowSpec src = specRepo.get(id);
        if (src == null) return Result.fail(404, "not found: " + id);
        WorkflowSpec copy = new WorkflowSpec();
        copy.setName(src.getName() + " (副本)");
        copy.setDescription(src.getDescription());
        copy.setSteps(src.getSteps());
        WorkflowSpec saved = specRepo.save(copy);
        return Result.success(saved);
    }

    // ============== 执行 ==============
    @PostMapping("/run")
    public Result<String> run(@RequestBody WorkflowSpec spec, HttpServletRequest req) {
        String runId = "wf-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String user = req.getHeader("X-Username");
        if (user == null) user = "anonymous";

        // 如果 spec.id 已存在, 走 spec 仓库定义的
        if (spec.getId() != null && !spec.getId().isBlank()) {
            specRepo.save(spec);
            specRepo.incrementRunCount(spec.getId());
        }

        log.info("[WF] submit id={} name={} steps={} user={}",
                runId, spec.getName(), spec.getSteps() == null ? 0 : spec.getSteps().size(), user);
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
    public Result<WorkflowRun> getRun(@PathVariable String id) {
        WorkflowRun r = engine.get(id);
        if (r == null) return Result.fail(404, "run not found: " + id);
        return Result.success(r);
    }

    @GetMapping("/runs")
    public Result<Map<String, WorkflowRun>> listRuns() {
        return Result.success(engine.all());
    }

    // ============== 模板 ==============
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
