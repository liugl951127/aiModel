package com.aiplatform.workflow.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.workflow.engine.NodeExecutor;
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
    private final NodeExecutor nodeExecutor;

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

    // ============== 节点执行端点 (前端 Workflow.vue 调用) ==============
    /**
     * 单节点执行: 接收 nodeId + config + upstream, 路由到对应服务.
     *
     * <p>调用方: 前端工作流编排器每跑一步调一次, 或 SSE 模式订阅.</p>
     *
     * <pre>
     * POST /api/workflow/exec
     * {
     *   "nodeId": "kb_search",
     *   "config": { "query": "{{input}}", "topK": 3, "rerank": true },
     *   "upstream": "什么是 Seata"
     * }
     * </pre>
     */
    @PostMapping("/exec")
    public Result<java.util.Map<String, Object>> execNode(@RequestBody java.util.Map<String, Object> body) {
        String nodeId = (String) body.get("nodeId");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> config = (java.util.Map<String, Object>) body.get("config");
        Object upstream = body.get("upstream");
        if (nodeId == null || nodeId.isBlank()) {
            return Result.fail(400, "nodeId 不能为空");
        }
        log.info("[WF] exec node={}, hasConfig={}, hasUpstream={}", nodeId, config != null, upstream != null);
        java.util.Map<String, Object> out = nodeExecutor.execute(nodeId, config, upstream);
        return Result.success(out);
    }

    /**
     * 批量执行: 工作流引擎按依赖顺序跑所有节点, 上游输出喂下游.
     *
     * <p>调用方: 前端工作流 "运行" 按钮.</p>
     *
     * <pre>
     * POST /api/workflow/exec/batch
     * {
     *   "nodes": [
     *     { "id": "n1", "nodeId": "data_loader", "config": {...}, "deps": [] },
     *     { "id": "n2", "nodeId": "kb_search", "config": {...}, "deps": ["n1"] }
     *   ]
     * }
     * </pre>
     */
    @PostMapping("/exec/batch")
    public Result<java.util.List<java.util.Map<String, Object>>> execBatch(@RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> nodes =
                (java.util.List<java.util.Map<String, Object>>) body.get("nodes");
        if (nodes == null || nodes.isEmpty()) {
            return Result.fail(400, "nodes 不能为空");
        }
        // 简单按 deps 顺序执行 (无并行优化)
        java.util.Map<String, Object> outputs = new java.util.HashMap<>();
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        java.util.List<String> remaining = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> n : nodes) {
            remaining.add((String) n.get("id"));
        }
        int maxIter = nodes.size() * 2;  // 防环
        int iter = 0;
        while (!remaining.isEmpty() && iter++ < maxIter) {
            java.util.Iterator<String> it = remaining.iterator();
            while (it.hasNext()) {
                String nid = it.next();
                java.util.Map<String, Object> node = nodes.stream()
                        .filter(n -> nid.equals(n.get("id"))).findFirst().orElse(null);
                if (node == null) { it.remove(); continue; }
                @SuppressWarnings("unchecked")
                java.util.List<String> deps = (java.util.List<String>) node.getOrDefault("deps", java.util.List.of());
                boolean allDone = true;
                for (String d : deps) {
                    if (!outputs.containsKey(d)) { allDone = false; break; }
                }
                if (!allDone) continue;
                // 收集上游 (最后一个 dep 的输出)
                Object upstream = null;
                if (!deps.isEmpty()) {
                    upstream = outputs.get(deps.get(deps.size() - 1));
                }
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> cfg = (java.util.Map<String, Object>) node.get("config");
                String typeId = (String) node.get("nodeId");
                java.util.Map<String, Object> out = nodeExecutor.execute(typeId, cfg, upstream);
                outputs.put(nid, out);
                java.util.Map<String, Object> result = new java.util.HashMap<>();
                result.put("id", nid);
                result.put("nodeId", typeId);
                result.put("output", out);
                result.put("status", "ok");
                results.add(result);
                it.remove();
            }
        }
        if (!remaining.isEmpty()) {
            return Result.fail(500, "工作流存在循环依赖或缺失依赖: " + remaining);
        }
        return Result.success(results);
    }
}
