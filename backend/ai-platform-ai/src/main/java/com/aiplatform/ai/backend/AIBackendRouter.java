package com.aiplatform.ai.backend;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ★ AI 后端路由器 — 业务侧只跟本类打交道, 不直接 import 具体实现.
 *
 * <p>应用启动时:
 * <ol>
 *   <li>Spring 自动注入所有 AIBackend 实现 (Mock/Onnx/Ollama/Http)</li>
 *   <li>读取配置 aiplatform.ai.backend 选激活后端</li>
 *   <li>未匹配到时降级到 mock (永远有兜底)</li>
 * </ol>
 *
 * <p>业务调用统一走 router.chat() / embed() / rerank() / webSearch().</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIBackendRouter {

    private final List<AIBackend> backends;
    private volatile AIBackend active;

    @PostConstruct
    public void init() {
        // 找带 @Primary 注解的优先, 否则用第一个
        active = backends.stream()
                .filter(AIBackend::isHealthy)
                .findFirst()
                .orElse(backends.get(0));
        log.info("[AI Router] 已激活后端: {} (可选: {})",
                active.name(),
                backends.stream().map(AIBackend::name).toList());
    }

    public AIBackend active() { return active; }

    public void switchTo(String backendName) {
        backends.stream()
                .filter(b -> b.name().equalsIgnoreCase(backendName))
                .findFirst()
                .ifPresent(b -> {
                    this.active = b;
                    log.info("[AI Router] 切换后端 -> {}", b.name());
                });
    }

    // ========== 代理到 active 后端 ==========
    public String chat(String system, List<Map<String, String>> messages, Map<String, Object> options) {
        return active.chat(system, messages, options);
    }

    public void chatStream(String system, List<Map<String, String>> messages,
                           Map<String, Object> options, java.util.function.Consumer<String> onChunk) {
        active.chatStream(system, messages, options, onChunk);
    }

    public float[] embed(String text) { return active.embed(text); }

    public List<float[]> embedBatch(List<String> texts) { return active.embedBatch(texts); }

    public List<AIBackend.RerankResult> rerank(String query, List<String> candidates, int topK) {
        return active.rerank(query, candidates, topK);
    }

    public List<AIBackend.WebSearchResult> webSearch(String query, int topK) {
        return active.webSearch(query, topK);
    }

    public boolean isHealthy() { return active.isHealthy(); }

    public List<String> availableBackends() {
        return backends.stream().map(AIBackend::name).toList();
    }
}
