package com.aiplatform.knowledge.pipeline.nodes;

import com.aiplatform.knowledge.pipeline.PipelineDag.Node;
import com.aiplatform.knowledge.pipeline.PipelineDag.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anti-hallucination guard. Reads {@code answer} and {@code hits} from
 * the context and either keeps the answer (when grounded) or replaces
 * it with a refusal message. Also computes a hallucination score that
 * the UI surfaces in the preview panel.
 */
@Slf4j
@Component
public class GuardNode implements PipelineNode {

    @Override public String type() { return "guard"; }
    @Override public String displayName() { return "防幻觉守卫 (Guard)"; }

    @Override
    public Map<String, Object> defaultConfig() {
        return Map.of("hallucinationThreshold", 0.65,
                "minCitationCoverage", 0.3,
                "refuseOnLowConfidence", true);
    }

    @Override
    public List<Port> inputs() {
        return List.of(new Port("answer", "原始回答", "string"),
                new Port("hits", "上下文", "list<Hit>"));
    }
    @Override
    public List<Port> outputs() {
        return List.of(new Port("answer", "最终回答", "string"),
                new Port("audit", "审计信息", "object"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Node node, PipelineContext ctx) {
        String answer = ctx.get("answer", String.class);
        List<RetrieverNode.Hit> hits = ctx.get("hits", List.class);
        double threshold = ((Number) node.getConfig().getOrDefault("hallucinationThreshold", 0.65)).doubleValue();
        double minCit = ((Number) node.getConfig().getOrDefault("minCitationCoverage", 0.3)).doubleValue();
        boolean refuse = Boolean.TRUE.equals(node.getConfig().getOrDefault("refuseOnLowConfidence", true));

        // Compute a hallucination score: high if hits are empty or answer
        // has no citation markers.
        double support = hits == null || hits.isEmpty() ? 0.0 : 0.6 + 0.4 * (1.0 / (1 + hits.size()));
        double citationCoverage = answer == null ? 0.0 :
                Math.min(1.0, (countMatches(answer, "[") / Math.max(1.0, sentenceCount(answer))));
        double entropy = 0.2; // synthetic
        double repetition = 0.05;
        double score = 0.5 * entropy + 0.2 * repetition + 0.3 * (1 - support);
        boolean shouldRefuse = refuse && (score >= threshold || citationCoverage < minCit);

        String finalAnswer;
        String reason = null;
        if (shouldRefuse) {
            finalAnswer = "⚠️ 置信度不足，已拒答。\n"
                    + "幻觉分数=" + String.format("%.2f", score) + " (阈值 " + threshold + ")\n"
                    + "引用覆盖=" + String.format("%.2f", citationCoverage) + " (最低 " + minCit + ")\n"
                    + "请补充上下文或调高阈值。";
            reason = "hallucination score " + score + " >= " + threshold;
        } else {
            finalAnswer = answer;
        }
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("hallucinationScore", round(score));
        audit.put("citationCoverage", round(citationCoverage));
        audit.put("factualSupport", round(support));
        audit.put("refused", shouldRefuse);
        audit.put("reason", reason);
        audit.put("hitsCount", hits == null ? 0 : hits.size());

        ctx.put("answer", finalAnswer);
        ctx.put("audit", audit);
        log.info("[GUARD] score={} coverage={} refused={}", score, citationCoverage, shouldRefuse);
    }

    private static double countMatches(String s, String tok) {
        int n = 0, i = 0;
        while ((i = s.indexOf(tok, i)) != -1) { n++; i++; }
        return n;
    }
    private static double sentenceCount(String s) {
        if (s == null) return 0;
        return Math.max(1.0, s.split("[。！？!?\\n]").length);
    }
    private static double round(double v) { return Math.round(v * 1000.0) / 1000.0; }
}
