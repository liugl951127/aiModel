package com.aiplatform.ai.backend;

import java.util.List;
import java.util.Map;

/**
 * ★ AI 后端统一抽象 (替代所有外部联网 API)
 *
 * <p>原项目里所有"调外部"的代码 (LLM/Embedding/Rerank/WebSearch) 都收口到这 4 个方法.
 * 业务侧只依赖本接口, 不直接 import 具体实现, 通过 application.yml 切后端.</p>
 *
 * <h2>支持的后端</h2>
 * <ul>
 *   <li>{@code mock}      - 离线演示, 不联网, 不真推理 (默认)</li>
 *   <li>{@code onnx}      - 本地 ONNX Runtime, 真模型推理 (BGE/Qwen)</li>
 *   <li>{@code ollama}    - 调本地 Ollama 服务 (兼容 llama.cpp / qwen2.5 等)</li>
 *   <li>{@code http}      - 通用 HTTP, 兼容远端 OpenAI-compatible API</li>
 * </ul>
 *
 * <h2>路由示例</h2>
 * <pre>
 *   aiplatform:
 *     ai:
 *       backend: onnx
 *       onnx:
 *         chat-model: /opt/ai-platform/models/qwen2.5-1.5b-instruct-q4.onnx
 *         embed-model: /opt/ai-platform/models/bge-small-zh-v1.5.onnx
 * </pre>
 *
 * @author Mavis
 */
public interface AIBackend {

    /** 后端标识, 用于日志 + 配置 */
    String name();

    // ========== LLM 能力 ==========

    /**
     * 聊天/生成 (非流式). 用于 ReAct 思考/回答/工具参数解析.
     *
     * @param system   系统 prompt (ReAct 指令)
     * @param messages 历史消息 (user/assistant/tool 交替)
     * @param options  可选: temperature / max_tokens / stop 等
     * @return 模型完整回复
     */
    String chat(String system, List<Map<String, String>> messages, Map<String, Object> options);

    /**
     * 流式聊天 (SSE). 用于 /api/conversation/chat 流式输出.
     * 默认实现: 调 chat() 整段返回.
     *
     * @param onChunk 每收到一段 token 就回调
     */
    default void chatStream(String system, List<Map<String, String>> messages,
                            Map<String, Object> options, java.util.function.Consumer<String> onChunk) {
        String full = chat(system, messages, options);
        onChunk.accept(full);
    }

    // ========== Embedding 能力 ==========

    /**
     * 单文本向量化. 维度由后端决定 (BGE-small=512, BGE-large=1024).
     */
    float[] embed(String text);

    /**
     * 批量向量化.
     */
    default List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    // ========== Rerank 能力 ==========

    /**
     * 重排序. 给定 query + 一组候选, 按相关性倒序返回.
     * 用于 RAG 检索后 rerank 提精.
     */
    List<RerankResult> rerank(String query, List<String> candidates, int topK);

    /** Rerank 单条结果 */
    record RerankResult(int index, float score) {}

    // ========== 联网搜索能力 (本地实现) ==========

    /**
     * 联网搜索 — 本地实现: 查本地知识库 / 缓存 / 内置 corpus.
     * 不再调外部 (如 DuckDuckGo), 完全自包含.
     *
     * @param query   搜索词
     * @param topK    返回前 N 条
     * @return 搜索结果 (title + url + snippet)
     */
    List<WebSearchResult> webSearch(String query, int topK);

    /** WebSearch 单条结果 */
    record WebSearchResult(String title, String url, String snippet, double score) {}

    // ========== 健康 ==========

    /** 后端健康检查 */
    boolean isHealthy();
}
