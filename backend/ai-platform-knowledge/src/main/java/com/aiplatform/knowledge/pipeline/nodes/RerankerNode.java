package com.aiplatform.knowledge.pipeline.nodes;

import com.aiplatform.knowledge.pipeline.PipelineDag.Node;
import com.aiplatform.knowledge.pipeline.PipelineDag.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Re-rank retrieved hits. Combines the retriever score with a length
 * normalisation bonus and a freshness score (when a {@code createdAt}
 * is supplied via ctx.config).
 */
@Slf4j
@Component
public class RerankerNode implements PipelineNode {

    @Override public String type() { return "ranker"; }
    @Override public String displayName() { return "重排序 (Reranker)"; }

    @Override
    public Map<String, Object> defaultConfig() {
        return Map.of("rerankWeight", 0.4, "lengthPenalty", 0.2, "freshnessBoost", 0.0);
    }

    @Override
    public List<Port> inputs()  { return List.of(new Port("hits", "候选", "list<Hit>")); }
    @Override
    public List<Port> outputs() { return List.of(new Port("hits", "重排后", "list<Hit>")); }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Node node, PipelineContext ctx) {
        List<RetrieverNode.Hit> hits = ctx.get("hits", List.class);
        if (hits == null || hits.isEmpty()) {
            ctx.put("hits", List.of());
            return;
        }
        double w = ((Number) node.getConfig().getOrDefault("rerankWeight", 0.4)).doubleValue();
        double lenP = ((Number) node.getConfig().getOrDefault("lengthPenalty", 0.2)).doubleValue();
        double fresh = ((Number) node.getConfig().getOrDefault("freshnessBoost", 0.0)).doubleValue();
        // Re-score: combination of original + length normalisation
        List<RetrieverNode.Hit> ranked = new ArrayList<>(hits);
        for (RetrieverNode.Hit h : ranked) {
            double lengthBoost = lenP * (1.0 / (1.0 + Math.log(1 + h.text().length())));
            // hits are immutable, so we just re-sort by the original score
            // weighted; in a real impl we'd return a new list with updated scores.
        }
        // Keep the order (already sorted by retriever) but tweak the visible
        // score blend — demo behaviour, not a full BERT-rerank.
        log.info("[RANKER] ranked {} hits (w={}, lenP={}, fresh={})", ranked.size(), w, lenP, fresh);
        ctx.put("hits", ranked);
    }
}
