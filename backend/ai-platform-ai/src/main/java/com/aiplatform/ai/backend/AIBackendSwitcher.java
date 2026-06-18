package com.aiplatform.ai.backend;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ★ AI 后端切换器 — 业务侧统一入口, 同时支持:
 * <ul>
 *   <li><b>chat backend</b>: mock / internal / onnx / ollama / http (由 aiplatform.ai.backend 决定)</li>
 *   <li><b>search mode</b>: internal / external (由 aiplatform.ai.search-mode 决定, 独立)</li>
 * </ul>
 *
 * <h2>设计目的</h2>
 * <p>允许用户:
 * <ul>
 *   <li>用本地 onnx 做 chat (省钱), 但 webSearch 走外部 DuckDuckGo (查最新信息)</li>
 *   <li>或反之: chat 走 http (OpenAI), webSearch 走 internal (查自己知识库)</li>
 *   <li>或全本地: chat=onnx, search=internal</li>
 * </ul>
 *
 * <p>业务侧只跟本类打交道, 完全不感知后端细节.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIBackendSwitcher {

    private final AIBackendRouter chatRouter;        // chat/embed/rerank 用
    private final List<ExternalWebSearchAdapter> externalAdapters;

    @Value("${aiplatform.ai.search-mode:internal}")
    private String searchMode;  // internal | external

    /**
     * chat — 走 chatRouter (配置 backend 决定)
     */
    public String chat(String system, List<Map<String, String>> messages, Map<String, Object> options) {
        return chatRouter.chat(system, messages, options);
    }

    public void chatStream(String system, List<Map<String, String>> messages,
                           Map<String, Object> options, java.util.function.Consumer<String> onChunk) {
        chatRouter.chatStream(system, messages, options, onChunk);
    }

    /**
     * embed / rerank — 走 chatRouter (本系统都基于同一后端)
     */
    public float[] embed(String text) { return chatRouter.embed(text); }

    public List<float[]> embedBatch(List<String> texts) { return chatRouter.embedBatch(texts); }

    public List<AIBackend.RerankResult> rerank(String query, List<String> candidates, int topK) {
        return chatRouter.rerank(query, candidates, topK);
    }

    /**
     * webSearch — 按 search-mode 路由
     * <ul>
     *   <li>internal: 走 internal 后端 (本系统知识库 + 内置 corpus)</li>
     *   <li>external: 走外部适配器 (DuckDuckGo / 远端 API, 不去掉)</li>
     * </ul>
     */
    public List<AIBackend.WebSearchResult> webSearch(String query, int topK) {
        if ("external".equalsIgnoreCase(searchMode) && !externalAdapters.isEmpty()) {
            // 优先用第一个可用外部适配器
            for (ExternalWebSearchAdapter adapter : externalAdapters) {
                if (adapter.isAvailable()) {
                    try {
                        List<AIBackend.WebSearchResult> r = adapter.search(query, topK);
                        if (!r.isEmpty()) {
                            log.debug("[search] 走外部: {}", adapter.name());
                            return r;
                        }
                    } catch (Exception e) {
                        log.warn("[search] 外部失败 {}: {}", adapter.name(), e.getMessage());
                    }
                }
            }
        }
        // 默认 internal
        log.debug("[search] 走内部 (search-mode={})", searchMode);
        return chatRouter.webSearch(query, topK);
    }

    public boolean isHealthy() { return chatRouter.isHealthy(); }

    public String currentChatBackend() { return chatRouter.active().name(); }
    public String currentSearchMode() { return searchMode; }
}
