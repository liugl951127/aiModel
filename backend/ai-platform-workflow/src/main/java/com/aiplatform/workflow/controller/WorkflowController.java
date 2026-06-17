package com.aiplatform.workflow.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.workflow.engine.NodeExecutor;
import com.aiplatform.workflow.engine.WorkflowEngine;
import com.aiplatform.workflow.entity.WorkflowSpecEntity;
import com.aiplatform.workflow.model.WorkflowRun;
import com.aiplatform.workflow.model.WorkflowSpec;
import com.aiplatform.workflow.service.WorkflowSpecRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Workflow orchestration REST surface.
 *
 * <p>v2: 改用 DB 持久化 (workflow_spec 表), 替换原 ConcurrentHashMap 内存版.
 * 前端发 {name, nodes:[...], edges:[...]} → 后端 JsonNode 接住, 原样存 specJson.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowSpecRepository specRepo;
    private final WorkflowEngine engine;
    private final NodeExecutor nodeExecutor;
    private static final ObjectMapper OM = new ObjectMapper();

    // ============== CRUD ==============

    /**
     * 保存工作流定义. body 形如 {name, nodes:[{id,type,name,x,y,params}], edges:[...]}.
     */
    @PostMapping("/spec")
    public Result<Map<String, Object>> saveSpec(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String user = req.getHeader("X-Username");
        if (user == null) user = "anonymous";

        String name = body.get("name") == null ? "(未命名)" : body.get("name").toString();
        String description = body.get("description") == null ? null : body.get("description").toString();

        // 算 nodeCount / edgeCount
        Object nodesObj = body.get("nodes");
        Object edgesObj = body.get("edges");
        int nodeCount = nodesObj instanceof List ? ((List<?>) nodesObj).size() : 0;
        int edgeCount = edgesObj instanceof List ? ((List<?>) edgesObj).size() : 0;

        // 重新拼 body 完整 (含 nodes, edges, viewport, etc) → specJson
        String specJson;
        try {
            specJson = OM.writeValueAsString(body);
        } catch (Exception e) {
            return Result.fail(500, "specJson 序列化失败: " + e.getMessage());
        }

        WorkflowSpecEntity saved = specRepo.save(name, user, description, specJson, nodeCount, edgeCount);
        log.info("[WF] save id={} name={} author={}", saved.getId(), saved.getName(), user);
        return Result.success(toSimpleMap(saved));
    }

    @GetMapping("/spec/list")
    public Result<List<Map<String, Object>>> listSpecs() {
        return Result.success(specRepo.listSimple());
    }

    @GetMapping("/spec/{id}")
    public Result<Map<String, Object>> getSpec(@PathVariable Long id) {
        WorkflowSpecEntity e = specRepo.getById(id);
        if (e == null) return Result.fail(404, "workflow spec not found: " + id);
        return Result.success(toFullMap(e));
    }

    @DeleteMapping("/spec/{id}")
    public Result<Void> deleteSpec(@PathVariable Long id) {
        specRepo.delete(id);
        return Result.success();
    }

    @PostMapping("/spec/{id}/duplicate")
    public Result<Map<String, Object>> duplicateSpec(@PathVariable Long id, HttpServletRequest req) {
        String user = req.getHeader("X-Username");
        if (user == null) user = "anonymous";
        WorkflowSpecEntity copy = specRepo.duplicate(id, user);
        if (copy == null) return Result.fail(404, "not found: " + id);
        return Result.success(toFullMap(copy));
    }

    // ============== 执行 ==============

    @PostMapping("/run")
    public Result<String> run(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String user = req.getHeader("X-Username");
        if (user == null) user = "anonymous";
        log.info("[WF] run triggered by user={}", user);

        // body: {specId: 123} 或者 {spec: {name,nodes,edges}}
        Object specIdObj = body.get("specId");
        Long specId = null;
        WorkflowSpecEntity entity = null;
        WorkflowSpec spec = null;

        if (specIdObj instanceof Number) {
            specId = ((Number) specIdObj).longValue();
            entity = specRepo.getById(specId);
            if (entity == null) return Result.fail(404, "spec not found: " + specId);
            spec = specRepo.toLegacySpec(entity);
        } else if (body.get("spec") instanceof Map) {
            // 直接传 spec 进来 (不存)
            @SuppressWarnings("unchecked")
            Map<String, Object> specMap = (Map<String, Object>) body.get("spec");
            spec = parseSpecFromMap(specMap);
        } else if (body.containsKey("nodes") && body.containsKey("edges")) {
            // 前端 WorkflowList 走老逻辑传 {id,name,nodes,edges}, 直接取
            spec = parseSpecFromMap(body);
        } else {
            return Result.fail(400, "body 必须含 specId 或 spec");
        }

        String runId = engine.run(spec).getRunId();
        if (entity != null) specRepo.incrRunCount(entity.getId());
        return Result.success(runId);
    }

    @GetMapping("/runs")
    public Result<List<Map<String, Object>>> listRuns(@RequestParam(defaultValue = "100") int limit) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        // 1. DB 历史 (优先, 重启不丢)
        for (com.aiplatform.workflow.entity.WorkflowRunEntity e : engine.listFromDb(limit)) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", e.getRunId());
            m.put("specId", e.getSpecId());
            m.put("specName", e.getSpecName());
            m.put("status", e.getStatus());
            m.put("progress", e.getProgress());
            m.put("currentStep", e.getCurrentStep());
            m.put("failedNodeId", e.getFailedNodeId());
            m.put("failedNodeName", e.getFailedNodeName());
            m.put("failedReason", e.getFailedReason());
            m.put("durationMs", e.getDurationMs());
            m.put("startedAt", e.getStartedAt());
            m.put("finishedAt", e.getFinishedAt());
            m.put("source", "db");
            rows.add(m);
        }
        // 2. 内存 (实时, 覆盖 DB 同 runId)
        Map<String, WorkflowRun> runs = engine.all();
        for (Map.Entry<String, WorkflowRun> en : runs.entrySet()) {
            WorkflowRun r = en.getValue();
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", r.getRunId());
            m.put("specName", r.getWorkflowName());
            m.put("status", r.getStatus() == null ? null : r.getStatus().name());
            m.put("progress", r.getProgress());
            m.put("currentStep", r.getCurrentStep());
            m.put("error", r.getError());
            m.put("startedAt", r.getStartedAt());
            m.put("finishedAt", r.getFinishedAt());
            m.put("source", "memory");
            // 实时状态放最前 + 覆盖 DB 同 runId
            rows.removeIf(x -> java.util.Objects.equals(x.get("id"), r.getRunId()));
            rows.add(0, m);
        }
        return Result.success(rows);
    }

    @GetMapping("/run/{id}")
    public Result<Map<String, Object>> getRun(@PathVariable String id) {
        // 优先内存 (实时), 没取 DB (历史)
        WorkflowRun live = engine.get(id);
        if (live != null) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("source", "memory");
            m.put("runId", live.getRunId());
            m.put("workflowId", live.getWorkflowId());
            m.put("specName", live.getWorkflowName());
            m.put("status", live.getStatus() == null ? null : live.getStatus().name());
            m.put("progress", live.getProgress());
            m.put("currentStep", live.getCurrentStep());
            m.put("error", live.getError());
            m.put("startedAt", live.getStartedAt());
            m.put("finishedAt", live.getFinishedAt());
            return Result.success(m);
        }
        // DB 历史 (重启后能查)
        com.aiplatform.workflow.entity.WorkflowRunEntity dbRun = engine.getFromDb(id);
        if (dbRun == null) return Result.fail(404, "run not found: " + id);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("source", "db");
        m.put("runId", dbRun.getRunId());
        m.put("specId", dbRun.getSpecId());
        m.put("specName", dbRun.getSpecName());
        m.put("status", dbRun.getStatus());
        m.put("progress", dbRun.getProgress());
        m.put("currentStep", dbRun.getCurrentStep());
        m.put("output", dbRun.getOutput());
        m.put("failedNodeId", dbRun.getFailedNodeId());
        m.put("failedNodeName", dbRun.getFailedNodeName());
        m.put("failedReason", dbRun.getFailedReason());
        m.put("durationMs", dbRun.getDurationMs());
        m.put("startedAt", dbRun.getStartedAt());
        m.put("finishedAt", dbRun.getFinishedAt());
        return Result.success(m);
    }

    // ============== 单节点 exec (供编排运行实时调) ==============

    @PostMapping("/exec")
    public Result<Map<String, Object>> exec(@RequestBody Map<String, Object> body) {
        String nodeId = (String) body.getOrDefault("nodeId", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> cfg = (Map<String, Object>) body.getOrDefault("input", java.util.Collections.emptyMap());
        Object upstream = body.get("upstream");
        Map<String, Object> result = nodeExecutor.execute(nodeId, cfg, upstream);
        return Result.success(result);
    }

    // ============== helper ==============

    /**
     * 从 {name,nodes,edges} map 解析出 WorkflowSpec (steps).
     */
    private WorkflowSpec parseSpecFromMap(Map<String, Object> specMap) {
        WorkflowSpec spec = new WorkflowSpec();
        spec.setName((String) specMap.get("name"));
        spec.setDescription((String) specMap.get("description"));
        List<WorkflowSpec.Step> steps = new ArrayList<>();
        Object nodesObj = specMap.get("nodes");
        Object edgesObj = specMap.get("edges");
        Map<String, List<String>> deps = new HashMap<>();
        if (edgesObj instanceof List) {
            for (Object eo : (List<?>) edgesObj) {
                if (!(eo instanceof Map)) continue;
                Map<?, ?> edge = (Map<?, ?>) eo;
                String from = String.valueOf(edge.get("from"));
                String to = String.valueOf(edge.get("to"));
                if (from.isEmpty() || to.isEmpty() || "null".equals(to)) continue;
                deps.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
            }
        }
        if (nodesObj instanceof List) {
            for (Object no : (List<?>) nodesObj) {
                if (!(no instanceof Map)) continue;
                Map<?, ?> node = (Map<?, ?>) no;
                WorkflowSpec.Step s = new WorkflowSpec.Step();
                s.setType(String.valueOf(node.get("type")));
                String name = String.valueOf(node.get("name"));
                if (name.isEmpty() || "null".equals(name)) name = String.valueOf(node.get("id"));
                s.setName(name);
                s.setDependsOn(deps.getOrDefault(String.valueOf(node.get("id")), Collections.emptyList()));
                Object params = node.get("params");
                if (params instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> p = (Map<String, Object>) params;
                    s.setParams(p);
                } else {
                    s.setParams(new HashMap<>());
                }
                steps.add(s);
            }
        }
        spec.setSteps(steps);
        return spec;
    }

    private Map<String, Object> toSimpleMap(WorkflowSpecEntity e) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("author", e.getAuthor());
        m.put("description", e.getDescription());
        m.put("nodeCount", e.getNodeCount());
        m.put("edgeCount", e.getEdgeCount());
        m.put("runCount", e.getRunCount());
        m.put("lastRunAt", e.getLastRunAt());
        m.put("createTime", e.getCreateTime());
        m.put("updateTime", e.getUpdateTime());
        return m;
    }

    private Map<String, Object> toFullMap(WorkflowSpecEntity e) {
        Map<String, Object> m = toSimpleMap(e);
        // 把 specJson 解出来作为 spec 字段 (前端期望 body.nodes/edges)
        if (e.getSpecJson() != null && !e.getSpecJson().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> json = OM.readValue(e.getSpecJson(), Map.class);
                m.putAll(json);
                m.put("spec", json);
            } catch (Exception ex) {
                log.warn("[WF] specJson 解析失败: {}", ex.getMessage());
            }
        }
        return m;
    }
}