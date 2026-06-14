package com.aiplatform.knowledge.pipeline;

import com.aiplatform.knowledge.pipeline.PipelineDag.Edge;
import com.aiplatform.knowledge.pipeline.PipelineDag.Node;
import com.aiplatform.knowledge.pipeline.PipelineDag.Pipeline;
import com.aiplatform.knowledge.pipeline.PipelineDag.PipelineContext;
import com.aiplatform.knowledge.pipeline.nodes.PipelineNode;
import com.aiplatform.knowledge.pipeline.registry.NodeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pipeline orchestrator. Stores pipelines (in-memory for the demo),
 * runs them in topological order, and produces a {@link RunResult} that
 * captures every node's timing + final outputs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final NodeRegistry nodeRegistry;
    /** In-memory pipeline store. Replace with MySQL in production. */
    private final Map<String, Pipeline> pipelines = new HashMap<>();
    private final Map<String, RunResult> runs = new HashMap<>();

    /* ---------------- pipeline CRUD ---------------- */

    public Pipeline save(Pipeline p) {
        if (p.getId() == null) p.setId("pl-" + UUID.randomUUID().toString().substring(0, 8));
        // validate
        for (Node n : p.getNodes()) {
            if (nodeRegistry.get(n.getType()) == null) {
                throw new IllegalArgumentException("unknown node type: " + n.getType());
            }
        }
        validateEdges(p);
        pipelines.put(p.getId(), p);
        log.info("[PIPELINE] saved {} nodes={} edges={}", p.getId(),
                p.getNodes().size(), p.getEdges().size());
        return p;
    }

    public List<Pipeline> list() { return new ArrayList<>(pipelines.values()); }
    public Pipeline get(String id) { return pipelines.get(id); }
    public void delete(String id) { pipelines.remove(id); }

    /* ---------------- execution ---------------- */

    /**
     * Run a pipeline. Returns a {@link RunResult} with per-node timings
     * and the final context (containing all slots the nodes wrote).
     */
    public RunResult run(String pipelineId, String query, Map<String, Object> globalConfig) {
        Pipeline p = pipelines.get(pipelineId);
        if (p == null) throw new IllegalArgumentException("pipeline not found: " + pipelineId);
        PipelineContext ctx = new PipelineContext(query, globalConfig);
        ctx.put("query", query);
        List<String> order = topoSort(p);
        RunResult result = new RunResult();
        result.pipelineId = pipelineId;
        result.query = query;
        result.nodeResults = new LinkedHashMap<>();
        for (String nodeId : order) {
            Node n = findNode(p, nodeId);
            if (n == null) continue;
            long t0 = System.currentTimeMillis();
            try {
                PipelineNode impl = nodeRegistry.get(n.getType());
                impl.execute(n, ctx);
                result.nodeResults.put(nodeId, new NodeResult(true, null,
                        System.currentTimeMillis() - t0));
            } catch (Throwable th) {
                log.error("[PIPELINE] node {} failed: {}", n.getId(), th.getMessage());
                result.nodeResults.put(nodeId, new NodeResult(false, th.getMessage(),
                        System.currentTimeMillis() - t0));
                result.failed = true;
                break;
            }
        }
        result.finalAnswer = ctx.get("answer", String.class);
        result.audit = ctx.get("audit", Map.class);
        result.context = ctx.slots;
        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        result.runId = runId;
        runs.put(runId, result);
        return result;
    }

    public RunResult getRun(String runId) { return runs.get(runId); }

    /* ---------------- helpers ---------------- */

    private Node findNode(Pipeline p, String id) {
        for (Node n : p.getNodes()) if (n.getId().equals(id)) return n;
        return null;
    }

    private void validateEdges(Pipeline p) {
        for (Edge e : p.getEdges()) {
            if (findNode(p, e.getFrom()) == null || findNode(p, e.getTo()) == null) {
                throw new IllegalArgumentException("edge references unknown node: " + e);
            }
        }
    }

    private List<String> topoSort(Pipeline p) {
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> indeg = new HashMap<>();
        for (Node n : p.getNodes()) {
            adj.computeIfAbsent(n.getId(), k -> new ArrayList<>());
            indeg.putIfAbsent(n.getId(), 0);
        }
        for (Edge e : p.getEdges()) {
            adj.get(e.getFrom()).add(e.getTo());
            indeg.merge(e.getTo(), 1, Integer::sum);
        }
        java.util.Deque<String> q = new java.util.ArrayDeque<>();
        for (var en : indeg.entrySet()) if (en.getValue() == 0) q.add(en.getKey());
        List<String> order = new ArrayList<>();
        while (!q.isEmpty()) {
            String u = q.poll();
            order.add(u);
            for (String v : adj.getOrDefault(u, List.of())) {
                int d = indeg.merge(v, -1, Integer::sum);
                if (d == 0) q.add(v);
            }
        }
        if (order.size() != indeg.size()) {
            throw new IllegalArgumentException("pipeline has a cycle");
        }
        return order;
    }

    /** Single run result. */
    public static class RunResult {
        public String runId;
        public String pipelineId;
        public String query;
        public boolean failed;
        public String finalAnswer;
        public Map<String, Object> audit;
        public Map<String, Object> context;
        public Map<String, NodeResult> nodeResults;
    }
    public record NodeResult(boolean ok, String error, long elapsedMs) {}
}
