package com.aiplatform.knowledge.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.ResultCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapper around Elasticsearch. Falls back to an in-process inverted index if ES
 * is not reachable, so the platform still demos end-to-end without a running cluster.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    @Autowired(required = false)
    private ElasticsearchClient esClient;

    private final Map<String, Map<String, List<String>>> fallbackIndex = new ConcurrentHashMap<>();
    private boolean esAvailable = false;

    @PostConstruct
    public void init() {
        if (esClient == null) {
            log.warn("[KB] Elasticsearch client not configured, using in-process fallback index");
            esAvailable = false;
            return;
        }
        try {
            esClient.info();
            esAvailable = true;
            log.info("[KB] Elasticsearch connected");
        } catch (Exception e) {
            log.warn("[KB] Elasticsearch not reachable, fallback enabled: {}", e.getMessage());
            esAvailable = false;
        }
    }

    public void indexDocument(String indexName, String docId, String content, Map<String, Object> metadata) {
        if (esAvailable) {
            try {
                Map<String, Object> doc = new HashMap<>();
                doc.put("content", content);
                doc.putAll(metadata);
                esClient.index(i -> i.index(indexName).id(docId).document(doc));
                return;
            } catch (IOException e) {
                log.warn("[KB] index failed, switching to fallback: {}", e.getMessage());
                esAvailable = false;
            }
        }
        fallbackIndex.computeIfAbsent(indexName, k -> new ConcurrentHashMap<>())
                .put(docId, List.of(content, String.valueOf(metadata)));
    }

    public List<Map<String, Object>> search(String indexName, String query, int topK) {
        final int k = topK <= 0 ? 3 : topK;
        if (esAvailable) {
            try {
                SearchResponse<Map> resp = esClient.search(s -> s
                                .index(indexName)
                                .size(k)
                                .query(Query.of(q -> q.match(m -> m.field("content").query(query)))),
                        Map.class);
                List<Map<String, Object>> out = new ArrayList<>();
                for (Hit<Map> hit : resp.hits().hits()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", hit.id());
                    m.put("score", hit.score());
                    Object src = hit.source();
                    if (src instanceof Map<?, ?> sm) {
                        for (Map.Entry<?, ?> e : sm.entrySet()) {
                            m.put(String.valueOf(e.getKey()), e.getValue());
                        }
                    }
                    out.add(m);
                }
                return out;
            } catch (IOException e) {
                log.warn("[KB] search failed, using fallback: {}", e.getMessage());
                esAvailable = false;
            }
        }

        // Fallback: simple substring scoring
        Map<String, List<String>> docs = fallbackIndex.getOrDefault(indexName, Map.of());
        List<Map<String, Object>> scored = new ArrayList<>();
        for (var e : docs.entrySet()) {
            String content = e.getValue().get(0);
            if (content == null) continue;
            String lower = content.toLowerCase();
            String q = query.toLowerCase();
            int idx = 0;
            int hits = 0;
            while ((idx = lower.indexOf(q, idx)) != -1) {
                hits++;
                idx += q.length();
            }
            if (hits > 0) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", e.getKey());
                m.put("score", (double) hits);
                m.put("content", snippet(content, q, 200));
                scored.add(m);
            }
        }
        scored.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));
        return scored.subList(0, Math.min(k, scored.size()));
    }

    private String snippet(String content, String query, int maxLen) {
        int idx = content.toLowerCase().indexOf(query);
        if (idx < 0) return content.substring(0, Math.min(maxLen, content.length()));
        int from = Math.max(0, idx - 50);
        int to = Math.min(content.length(), idx + maxLen);
        return (from > 0 ? "..." : "") + content.substring(from, to) + (to < content.length() ? "..." : "");
    }

    public boolean isEsAvailable() {
        return esAvailable;
    }

    public void ensureIndex(String indexName) {
        if (esAvailable) {
            try {
                boolean exists = esClient.indices().exists(e -> e.index(indexName)).value();
                if (!exists) {
                    esClient.indices().create(c -> c.index(indexName));
                }
            } catch (IOException e) {
                throw new BusinessException(ResultCode.KNOWLEDGE_INDEX_ERROR, "创建索引失败: " + e.getMessage());
            }
        }
        // No-op for fallback.
    }
}
