package com.aiplatform.knowledge.pipeline.nodes;

import com.aiplatform.knowledge.pipeline.PipelineDag.Node;
import com.aiplatform.knowledge.pipeline.PipelineDag.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Top-k lexical retriever (BM25-style). In production this would call
 * Elasticsearch via the existing {@code KnowledgeSearchService}. Here we
 * score against the input {@code chunks} slot using simple term overlap
 * so the pipeline is self-contained and runs in the demo without ES.
 */
@Slf4j
@Component
public class RetrieverNode implements PipelineNode {

    @Override public String type() { return "retriever"; }
    @Override public String displayName() { return "检索器 (Retriever)"; }

    @Override
    public Map<String, Object> defaultConfig() {
        return Map.of("topK", 5, "similarityThreshold", 0.0);
    }

    @Override
    public List<Port> inputs() {
        return List.of(new Port("chunks", "候选文档", "list<string>"),
                new Port("query", "查询", "string"));
    }

    @Override
    public List<Port> outputs() {
        return List.of(new Port("hits", "命中", "list<Hit>"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Node node, PipelineContext ctx) {
        List<String> chunks = ctx.get("chunks", List.class);
        String query = ctx.get("query", String.class);
        if (query == null) query = ctx.query;
        if (chunks == null || chunks.isEmpty() || query == null || query.isBlank()) {
            ctx.put("hits", List.of());
            return;
        }
        int topK = (int) node.getConfig().getOrDefault("topK", 5);
        double threshold = ((Number) node.getConfig().getOrDefault("similarityThreshold", 0.0)).doubleValue();
        List<String> qTokens = tokenize(query);
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            double s = jaccard(qTokens, tokenize(chunks.get(i)));
            if (s >= threshold) hits.add(new Hit(i, chunks.get(i), s));
        }
        hits.sort((a, b) -> Double.compare(b.score, a.score));
        if (hits.size() > topK) hits = hits.subList(0, topK);
        ctx.put("hits", hits);
        log.info("[RETRIEVER] q='{}' candidates={} hits={}", query, chunks.size(), hits.size());
    }

    private static List<String> tokenize(String s) {
        if (s == null) return List.of();
        String[] t = s.toLowerCase().split("[^a-z0-9一-鿿]+");
        java.util.List<String> out = new ArrayList<>();
        for (String x : t) if (!x.isBlank()) out.add(x);
        return out;
    }

    private static double jaccard(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        java.util.Set<String> A = new java.util.HashSet<>(a);
        java.util.Set<String> B = new java.util.HashSet<>(b);
        java.util.Set<String> inter = new java.util.HashSet<>(A); inter.retainAll(B);
        java.util.Set<String> uni = new java.util.HashSet<>(A); uni.addAll(B);
        return uni.isEmpty() ? 0 : (double) inter.size() / uni.size();
    }

    /** Single retrieval hit. */
    public record Hit(int index, String text, double score) {}
}
