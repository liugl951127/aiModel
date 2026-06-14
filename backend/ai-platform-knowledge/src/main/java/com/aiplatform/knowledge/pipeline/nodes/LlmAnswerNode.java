package com.aiplatform.knowledge.pipeline.nodes;

import com.aiplatform.knowledge.pipeline.PipelineDag.Node;
import com.aiplatform.knowledge.pipeline.PipelineDag.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Builds a prompt from the query + the top-k hits, then (in production)
 * POSTs to the inference service. In the demo path the answer is composed
 * locally so the pipeline runs end-to-end without a real LLM.
 */
@Slf4j
@Component
public class LlmAnswerNode implements PipelineNode {

    @Override public String type() { return "llm-answer"; }
    @Override public String displayName() { return "大模型作答 (LLM)"; }

    @Override
    public Map<String, Object> defaultConfig() {
        return Map.of("modelCode", "default", "temperature", 0.2, "maxTokens", 256);
    }

    @Override
    public List<Port> inputs()  {
        return List.of(new Port("hits", "上下文", "list<Hit>"),
                new Port("query", "问题", "string"));
    }
    @Override
    public List<Port> outputs() { return List.of(new Port("answer", "回答", "string")); }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Node node, PipelineContext ctx) {
        String query = ctx.get("query", String.class);
        if (query == null) query = ctx.query;
        List<RetrieverNode.Hit> hits = ctx.get("hits", List.class);
        StringBuilder prompt = new StringBuilder();
        prompt.append("Answer the question using ONLY the context below. If the answer is not in the context, say so.\n\n");
        if (hits != null) {
            int n = 0;
            for (RetrieverNode.Hit h : hits) {
                prompt.append("[").append(++n).append("] ").append(h.text()).append("\n");
            }
        }
        prompt.append("\nQ: ").append(query).append("\nA:");
        // In production: call InferenceService.generate(modelCode, prompt.toString(), maxTokens, temp)
        // For demo we synthesise a citation-rich answer.
        StringBuilder ans = new StringBuilder();
        if (hits != null && !hits.isEmpty()) {
            ans.append("根据检索结果，回答如下：");
            for (int i = 0; i < hits.size(); i++) {
                ans.append("\n[").append(i + 1).append("] ")
                        .append(snippet(hits.get(i).text()));
            }
        } else {
            ans.append("未在知识库中找到相关内容，建议补充资料或降低相似度阈值。");
        }
        ctx.put("answer", ans.toString());
        ctx.put("prompt", prompt.toString());
        log.info("[LLM] query='{}' hits={} answer.len={}", query,
                hits == null ? 0 : hits.size(), ans.length());
    }

    private static String snippet(String s) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }
}
