package com.aiplatform.trainer.rag;

import com.aiplatform.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索客户端。
 * <p>调用 {@code knowledge-service} 的搜索接口，返回拼接后的 top-K 段落
 * 文本。约束引擎只需要一个"字节包"验证接地，不需要结构化段落列表，
 * 所以本类只负责拼字符串、不负责排版。</p>
 *
 * <p>配置项：
 * <pre>
 *   aiplatform.knowledge.base-url   默认 http://127.0.0.1:9005
 * </pre>
 */
@Slf4j
@Component
public class KnowledgeRetriever {

    private final RestClient http;
    private final String baseUrl;

    public KnowledgeRetriever(@Value("${aiplatform.knowledge.base-url:http://127.0.0.1:9005}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 查询知识库并返回拼接后的 top-K 段落文本。
     * <p>出错时返回空字符串（调用方当作"没有接地上下文"）。</p>
     *
     * @param kbId  知识库 id（{@code null} = 任意）
     * @param query 自然语言查询
     * @param topK  合并的段落数
     * @return 拼接后的段落文本，永不为 {@code null}
     */
    public String retrieve(Long kbId, String query, int topK) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            StringBuilder url = new StringBuilder("/api/knowledge/search?query=").append(encoded)
                    .append("&topK=").append(Math.max(1, topK));
            if (kbId != null) url.append("&kbId=").append(kbId);
            @SuppressWarnings("rawtypes")
            Result<List> r = http.get().uri(url.toString()).retrieve().body(Result.class);
            if (r == null || r.getData() == null) return "";
            StringBuilder sb = new StringBuilder();
            for (Object o : r.getData()) {
                if (o instanceof Map<?, ?> m) {
                    Object text = m.get("text");
                    if (text != null) sb.append(text).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[RAG] retrieve failed kbId={} q='{}': {}", kbId, query, e.getMessage());
            return "";
        }
    }

    /** 供预览 UI 使用的 {@code search-all} 快捷调用。 */
    public String retrieveAll(String query, int topK) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            @SuppressWarnings("rawtypes")
            Result<String> r = http.get()
                    .uri("/api/knowledge/search-all?query=" + encoded + "&topK=" + Math.max(1, topK))
                    .retrieve()
                    .body(Result.class);
            return r == null || r.getData() == null ? "" : r.getData();
        } catch (Exception e) {
            log.warn("[RAG] retrieveAll failed: {}", e.getMessage());
            return "";
        }
    }

    /** 不需 RAG 的代码路径用的空上下文常量。 */
    public List<String> empty() { return Collections.emptyList(); }
}
