package com.aiplatform.knowledge.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 结果重排器。
 *
 * <p>ES 粗排后用启发式评分再过一遍，过滤掉低质量 / 重复段落。生产可替换
 * 为 BERT cross-encoder 或 LLM-based reranker。</p>
 *
 * <h2>启发式分数</h2>
 * <ul>
 *   <li>query 在 text 中覆盖率（按 jaccard）+ 0.5</li>
 *   <li>text 长度惩罚（&lt;50 字符 &gt;2000 字符的减分）</li>
 *   <li>段落去重：与已选结果 jaccard &gt; 0.85 的丢弃</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultReranker {

    private final QueryRewriter rewriter;

    /** 重新评分 + 截断到 topK + 去重。 */
    public List<RankedHit> rerank(String query, List<Hit> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        // 1. 评分
        List<RankedHit> scored = new ArrayList<>();
        for (Hit h : candidates) {
            double s = score(query, h.text);
            scored.add(new RankedHit(h, s));
        }
        // 2. 排序
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        // 3. MMR-like 去重
        List<RankedHit> out = new ArrayList<>();
        for (RankedHit rh : scored) {
            boolean dup = false;
            for (RankedHit kept : out) {
                if (QueryRewriter.jaccard(rh.hit.text, kept.hit.text) > 0.85) {
                    dup = true;
                    break;
                }
            }
            if (!dup) out.add(rh);
            if (out.size() >= topK) break;
        }
        log.debug("[RERANK] query='{}' in={} out={}", query, candidates.size(), out.size());
        return out;
    }

    /**
     * 单条评分：jaccard 主分 + 长度惩罚。
     */
    public double score(String query, String text) {
        if (text == null || text.isBlank()) return 0;
        double j = QueryRewriter.jaccard(query, text);
        double len = text.length();
        double lenScore;
        if (len < 50) lenScore = 0.3;
        else if (len > 2000) lenScore = 0.7;
        else lenScore = 1.0;
        // 关键词命中率（query 中的 token 出现率）
        Set<String> qt = new LinkedHashSet<>(Arrays.asList(
                query.toLowerCase().split("\\s+")));
        long hits = qt.stream().filter(t -> text.toLowerCase().contains(t)).count();
        double coverage = qt.isEmpty() ? 0 : (double) hits / qt.size();
        return j * 0.5 + coverage * 0.4 + lenScore * 0.1;
    }

    /** 输入。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Hit {
        private String id;
        private String text;
        private double esScore;
    }

    /** 输出：评分后的 hit。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RankedHit {
        private Hit hit;
        private double score;
    }
}
