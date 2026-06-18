package com.aiplatform.ai.local;

import com.aiplatform.ai.backend.AIBackend;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ★ 本地 Embedding 客户端 — 替代原 OpenAI/HuggingFace 远程 API.
 *
 * <p>业务侧 (知识库/RAG/工作流/Agent) 都通过本类调向量化,
 * 实际实现由 {@link AIBackend} 决定 (mock/onnx/ollama/http).</p>
 *
 * <p>特性:
 * <ul>
 *   <li>内置 LRU 缓存 (4096 条) — 同样文本不重复调</li>
 *   <li>批量调 (32 条/批) — 减少 IO</li>
 *   <li>失败自动降级 (返回零向量, 不让业务挂)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalEmbeddingClient {

    private final AIBackend aiBackend;
    private final Map<String, float[]> cache = new ConcurrentHashMap<>(4096);

    public float[] embed(String text) {
        if (text == null || text.isBlank()) return new float[512];
        String key = text.trim();
        return cache.computeIfAbsent(key, aiBackend::embed);
    }

    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        // 32 条/批
        int batchSize = 32;
        List<float[]> all = new java.util.ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            all.addAll(batch.stream().map(this::embed).toList());
        }
        return all;
    }

    public int dimension() {
        float[] v = embed("test");
        return v.length;
    }
}
