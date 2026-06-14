package com.aiplatform.workflow.engine;

import com.aiplatform.workflow.model.WorkflowRun;
import com.aiplatform.workflow.model.WorkflowSpec;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
     */
    public WorkflowRun run(WorkflowSpec spec) {
        WorkflowRun run = new WorkflowRun();
        run.setRunId(UUID.randomUUID().toString().substring(0, 8));
        run.setWorkflowId(spec.getId());
        run.setWorkflowName(spec.getName());
        run.setStatus(WorkflowRun.Status.RUNNING);
        runs.put(run.getRunId(), run);
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
                    throw new RuntimeException("step " + step.getName() + " failed: " + e.getMessage(), e);
                }
                run.setProgress(Math.min(100, run.getProgress() + Math.max(1, 100 / spec.getSteps().size())));
            });
            run.setStatus(WorkflowRun.Status.SUCCEEDED);
            run.setProgress(100);
        } catch (Throwable t) {
            log.error("[WF] run failed: {}", t.getMessage(), t);
            run.setStatus(WorkflowRun.Status.FAILED);
            run.setError(t.getMessage());
        } finally {
            run.setFinishedAt(java.time.Instant.now());
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
