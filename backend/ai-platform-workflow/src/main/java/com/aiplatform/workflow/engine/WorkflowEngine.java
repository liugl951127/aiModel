package com.aiplatform.workflow.engine;

import com.aiplatform.workflow.entity.WorkflowRunEntity;
import com.aiplatform.workflow.mapper.WorkflowRunMapper;
import com.aiplatform.workflow.model.WorkflowRun;
import com.aiplatform.workflow.model.WorkflowSpec;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives a {@link WorkflowSpec} by topological order. Steps are looked up
 * by their {@code type} field. Multiple runs are tracked in-memory and
 * can be inspected via the REST controller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final List<StepHandler> handlers;
    private final Map<String, StepHandler> registry = new HashMap<>();
    private final Map<String, WorkflowRun> runs = new ConcurrentHashMap<>();
    private final WorkflowRunMapper runMapper;  // ★ DB 持久化 (重启不丢)

    /** 服务启动后, 从 DB 加载最近 run (提供历史快照). */
    @jakarta.annotation.PostConstruct
    public void initFromDb() {
        try {
            List<WorkflowRunEntity> recent = runMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<WorkflowRunEntity>()
                    .eq("deleted", 0)
                    .orderByDesc("create_time")
                    .last("LIMIT 100")
            );
            log.info("[WF-DB] 从 DB 加载最近 {} 个 run 历史", recent.size());
        } catch (Exception e) {
            log.warn("[WF-DB] 启动加载历史 run 失败 (DB 可能未起): {}", e.getMessage());
        }
    }

    /** 从 DB 拿历史 run (供 API). */
    public List<WorkflowRunEntity> listFromDb(int limit) {
        try {
            return runMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<WorkflowRunEntity>()
                    .eq("deleted", 0)
                    .orderByDesc("create_time")
                    .last("LIMIT " + Math.max(1, Math.min(500, limit)))
            );
        } catch (Exception e) {
            log.warn("[WF-DB] listFromDb 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public WorkflowRunEntity getFromDb(String runId) {
        try {
            return runMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<WorkflowRunEntity>()
                    .eq("run_id", runId)
                    .eq("deleted", 0)
                    .last("LIMIT 1")
            );
        } catch (Exception e) {
            return null;
        }
    }

    @PostConstruct
    public void init() {
        for (StepHandler h : handlers) {
            registry.put(h.type(), h);
            log.info("[WF] registered handler: {}", h.type());
        }
    }

    /**
     * Synchronously run the workflow (caller can do this in a background
     * thread). Returns the populated {@link WorkflowRun}.
     *
     * <p>同时同步写 DB (workflow_run 表), 重启服务不丢运行历史.</p>
     */
    public WorkflowRun run(WorkflowSpec spec) {
        WorkflowRun run = new WorkflowRun();
        run.setRunId(UUID.randomUUID().toString().substring(0, 8));
        run.setWorkflowId(spec.getId());
        run.setWorkflowName(spec.getName());
        run.setStatus(WorkflowRun.Status.RUNNING);
        runs.put(run.getRunId(), run);

        // ★ DB: 起始记录 (status=RUNNING, progress=0, started_at=now)
        Long specIdNum = null;
        try { specIdNum = spec.getId() == null ? null : Long.parseLong(spec.getId()); } catch (Exception ignore) {}
        WorkflowRunEntity entity = new WorkflowRunEntity();
        entity.setRunId(run.getRunId());
        entity.setSpecId(specIdNum);
        entity.setSpecName(spec.getName());
        entity.setStatus("RUNNING");
        entity.setProgress(0);
        entity.setStartedAt(LocalDateTime.now());
        try { runMapper.insert(entity); } catch (Exception ex) { log.warn("[WF-DB] insert run 失败: {}", ex.getMessage()); }

        String[] failedNodeIdHolder = new String[]{null};
        String[] failedNodeNameHolder = new String[]{null};
        String[] failedReasonHolder = new String[]{null};
        long startMs = System.currentTimeMillis();
        try {
            topoBfs(spec).forEach(step -> {
                run.setCurrentStep(step.getName());
                StepHandler h = registry.get(step.getType());
                if (h == null) throw new IllegalStateException("no handler for type=" + step.getType());
                StepLog slog = new StepLog() {
                    @Override public void info(String m) { log.info("[WF:{}/{}] {}", run.getRunId(), step.getName(), m); }
                    @Override public void error(String m, Throwable t) { log.error("[WF:{}/{}] {}", run.getRunId(), step.getName(), m, t); }
                };
                try {
                    h.execute(step, run.getContext(), slog);
                } catch (Exception e) {
                    // ★ 记录失败节点 + 原因
                    failedNodeIdHolder[0] = step.getName();
                    failedNodeNameHolder[0] = step.getName();
                    failedReasonHolder[0] = e.getMessage();
                    throw new RuntimeException("step " + step.getName() + " failed: " + e.getMessage(), e);
                }
                run.setProgress(Math.min(100, run.getProgress() + Math.max(1, 100 / spec.getSteps().size())));
                // ★ DB: 进度 + currentStep
                try { runMapper.updateProgress(run.getRunId(), "RUNNING", run.getProgress(), step.getName()); }
                catch (Exception ex) { log.debug("[WF-DB] updateProgress 失败: {}", ex.getMessage()); }
            });
            run.setStatus(WorkflowRun.Status.SUCCEEDED);
            run.setProgress(100);
        } catch (Throwable t) {
            log.error("[WF] run failed: {}", t.getMessage(), t);
            run.setStatus(WorkflowRun.Status.FAILED);
            run.setError(t.getMessage());
            if (failedReasonHolder[0] == null) failedReasonHolder[0] = t.getMessage();
        } finally {
            run.setFinishedAt(java.time.Instant.now());
            // ★ DB: 完成时一次性更新
            long durationMs = System.currentTimeMillis() - startMs;
            try {
                String outputJson = com.alibaba.fastjson2.JSON.toJSONString(run.getContext());
                runMapper.updateFinish(run.getRunId(), run.getStatus().name(), run.getProgress(),
                        outputJson, failedNodeIdHolder[0], failedNodeNameHolder[0], failedReasonHolder[0],
                        durationMs, LocalDateTime.now(), LocalDateTime.now());
            } catch (Exception ex) { log.debug("[WF-DB] updateFinish 失败: {}", ex.getMessage()); }
        }
        return run;
    }

    public WorkflowRun get(String runId) { return runs.get(runId); }
    public Map<String, WorkflowRun> all() { return runs; }

    /** BFS over the dependsOn graph. Throws on cycles. */
    private List<WorkflowSpec.Step> topoBfs(WorkflowSpec spec) {
        Map<String, WorkflowSpec.Step> byName = new HashMap<>();
        for (WorkflowSpec.Step s : spec.getSteps()) byName.put(s.getName(), s);
        Map<String, Integer> indeg = new HashMap<>();
        for (WorkflowSpec.Step s : spec.getSteps()) indeg.put(s.getName(), s.getDependsOn() == null ? 0 : s.getDependsOn().size());
        Deque<WorkflowSpec.Step> queue = new ArrayDeque<>();
        for (WorkflowSpec.Step s : spec.getSteps()) if (indeg.get(s.getName()) == 0) queue.add(s);
        List<WorkflowSpec.Step> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            WorkflowSpec.Step s = queue.poll();
            order.add(s);
            for (WorkflowSpec.Step other : spec.getSteps()) {
                if (other.getDependsOn() != null && other.getDependsOn().contains(s.getName())) {
                    int d = indeg.get(other.getName()) - 1;
                    indeg.put(other.getName(), d);
                    if (d == 0) queue.add(other);
                }
            }
        }
        if (order.size() != spec.getSteps().size()) {
            throw new IllegalStateException("cycle in workflow spec");
        }
        return order;
    }
}
