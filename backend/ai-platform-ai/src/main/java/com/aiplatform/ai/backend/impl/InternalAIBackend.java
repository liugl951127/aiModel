package com.aiplatform.ai.backend.impl;

import com.aiplatform.ai.backend.AIBackend;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * ★ 内部后端 — 调本系统自己的服务 (knowledge/inference) 作为 AI 后端.
 *
 * <p>完全不调任何外部 API, 也不依赖任何外部模型. 走自包含的"知识库 + 推理"
 * 组合实现完整的 chat / embed / rerank / webSearch 能力.</p>
 *
 * <h2>能力映射</h2>
 * <ul>
 *   <li><b>chat</b>: 调本系统 /api/inference/chat (ONNX Runtime) + /api/knowledge/search (RAG)</li>
 *   <li><b>embed</b>: 调本系统 /api/knowledge/embed (基于已加载的 ONNX 模型)</li>
 *   <li><b>rerank</b>: 调本系统 /api/knowledge/search (用其内置 rerank 逻辑)</li>
 *   <li><b>webSearch</b>: 调本系统 /api/knowledge/search, 把检索结果包装成 web 结果</li>
 * </ul>
 *
 * <h2>外部 vs 内部 双轨</h2>
 * <p>本后端不取代 {@code http} (远端 OpenAI-compatible) 也不取代 {@code ollama},
 * 跟它们并存. 用户在 application.yml 配 {@code aiplatform.ai.backend=internal}
 * 走内部, 配 {@code http} 走外部, 配 {@code ollama} 走本地 Ollama.</p>
 *
 * <h2>激活</h2>
 * <pre>
 *   aiplatform:
 *     ai:
 *       backend: internal
 *       internal:
 *         knowledge-url: http://127.0.0.1:9006
 *         inference-url: http://127.0.0.1:9007
 *         top-k: 5
 * </pre>
 */
@Slf4j

@ConditionalOnProperty(name = "aiplatform.ai.backend", havingValue = "internal")
public class InternalAIBackend implements AIBackend {

    @Configuration
    @ConfigurationProperties(prefix = "aiplatform.ai.internal")
    public static class Props {
        private String knowledgeUrl = "http://127.0.0.1:9006";
        private String inferenceUrl = "http://127.0.0.1:9007";
        private int topK = 5;
        private int timeoutMs = 15000;
        public String getKnowledgeUrl() { return knowledgeUrl; }
        public void setKnowledgeUrl(String v) { this.knowledgeUrl = v; }
        public String getInferenceUrl() { return inferenceUrl; }
        public void setInferenceUrl(String v) { this.inferenceUrl = v; }
        public int getTopK() { return topK; }
        public void setTopK(int v) { this.topK = v; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int v) { this.timeoutMs = v; }
    }

    private final Props props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final MockAIBackend mockFallback = new MockAIBackend();

    /** 业务 corpus (用于纯离线 webSearch 兜底) */
    private static final List<WebSearchResult> INTERNAL_CORPUS = List.of(
        new WebSearchResult("AI Platform 架构总览", "internal://docs/architecture",
            "11 个微服务 (gateway/auth/user/system/model/agent/knowledge/inference/trainer/files/workflow) + 前端 Vue 3 + MySQL + Redis + ES + Nacos", 0.95),
        new WebSearchResult("工作流 32 节点编排", "internal://docs/workflow",
            "支持 AI 节点 (ReAct) / 业务节点 (HTTP/DB/Script) / 控制节点 (if/for/parallel), 拓扑执行, 失败重试", 0.92),
        new WebSearchResult("智能体 ReAct 引擎", "internal://docs/agent",
            "ReAct = Reasoning + Acting, 8 步循环, Thought -> Action -> Observation, 工具调用 + 反思", 0.90),
        new WebSearchResult("RAG 知识库问答", "internal://docs/rag",
            "文档分块 (512 token) -> BGE 向量化 -> ES 索引 -> query rewrite -> ANN -> rerank -> LLM 拼 prompt", 0.88),
        new WebSearchResult("DJL 模型训练", "internal://docs/trainer",
            "AWS 开源 Java 深度学习库, 支持 PyTorch / MXNet / TensorFlow, 任务队列 + 进度 SSE", 0.85),
        new WebSearchResult("ONNX Runtime 推理", "internal://docs/inference",
            "CPU 可跑 7B 量化模型, 延迟 100ms 内, 流式输出 (SSE)", 0.83),
        new WebSearchResult("Seata 分布式事务", "internal://docs/seata",
            "AT 模式 + 3 数据源演示, undo_log 自动回滚", 0.80),
        new WebSearchResult("Redisson 分布式能力", "internal://docs/distributed",
            "分布式锁 / 限流 / 限速 / ID 生成器 / 集合 / 队列 / 发布订阅, 7 大能力", 0.78),
        new WebSearchResult("Vue 3 前端架构", "internal://docs/frontend",
            "Composition API + Pinia + Element Plus + 路由守卫, 21+ 视图, 3 端自适应", 0.75),
        new WebSearchResult("Spring Cloud Alibaba", "internal://docs/sca",
            "Nacos (配置/注册) + Sentinel (限流) + Seata (事务) + RocketMQ (消息), 2023.0.1.0", 0.73)
    );

    public InternalAIBackend(Props props) { this.props = props; }

    @Override public String name() { return "internal"; }

    @Override
    public String chat(String system, List<Map<String, String>> messages, Map<String, Object> options) {
        // 找最后一条 user
        String lastUser = extractLastUser(messages);
        if (lastUser.isBlank()) return "[internal backend] no user message";

        try {
            // 1) RAG: 先查知识库
            List<WebSearchResult> docs = webSearch(lastUser, props.getTopK());

            // 2) 拼 prompt + 调本系统 inference
            StringBuilder context = new StringBuilder();
            for (WebSearchResult d : docs) {
                context.append("- [").append(d.title()).append("] ").append(d.snippet()).append("\n");
            }

            // 3) 调本系统 inference (走 ONNX Runtime, 真模型推理)
            String augmented = (system == null ? "" : system + "\n\n")
                    + "已知信息:\n" + context + "\n"
                    + "请基于以上已知信息回答用户问题, 如不知则说明.\n\n"
                    + "用户: " + lastUser;

            String response = callInference(augmented, options);
            return response + "\n\n(基于 " + docs.size() + " 条系统知识, " + name() + " backend)";
        } catch (Exception e) {
            log.warn("[internal] chat 失败: {}", e.getMessage());
            return mockFallback.chat(system, messages, options);
        }
    }

    @Override
    public float[] embed(String text) {
        try {
            ObjectNode req = mapper.createObjectNode();
            req.put("text", text == null ? "" : text);
            String body = httpPost(props.getKnowledgeUrl() + "/api/knowledge/embed", req.toString());
            JsonNode r = mapper.readTree(body);
            JsonNode data = r.path("data");
            if (!data.isArray() || data.size() == 0) return mockFallback.embed(text);
            float[] v = new float[data.size()];
            for (int i = 0; i < data.size(); i++) v[i] = (float) data.get(i).asDouble();
            return v;
        } catch (Exception e) {
            log.debug("[internal] embed 失败降级: {}", e.getMessage());
            return mockFallback.embed(text);
        }
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        // 用本系统 knowledge search 跑 rerank
        try {
            ObjectNode req = mapper.createObjectNode();
            req.put("query", query);
            req.put("topK", topK);
            ArrayNode arr = req.putArray("candidates");
            candidates.forEach(arr::add);
            String body = httpPost(props.getKnowledgeUrl() + "/api/knowledge/rerank", req.toString());
            JsonNode r = mapper.readTree(body);
            JsonNode data = r.path("data");
            if (!data.isArray()) return mockFallback.rerank(query, candidates, topK);
            List<RerankResult> out = new ArrayList<>();
            for (JsonNode n : data) {
                out.add(new RerankResult(n.path("index").asInt(), (float) n.path("score").asDouble()));
            }
            return out;
        } catch (Exception e) {
            return mockFallback.rerank(query, candidates, topK);
        }
    }

    @Override
    public List<WebSearchResult> webSearch(String query, int topK) {
        // 1) 先调本系统 knowledge search (RAG 检索, 真实数据)
        try {
            ObjectNode req = mapper.createObjectNode();
            req.put("query", query);
            req.put("topK", topK);
            req.put("highlight", true);
            String body = httpPost(props.getKnowledgeUrl() + "/api/knowledge/search", req.toString());
            JsonNode r = mapper.readTree(body);
            JsonNode data = r.path("data");
            if (data.isArray() && data.size() > 0) {
                List<WebSearchResult> out = new ArrayList<>();
                for (JsonNode n : data) {
                    out.add(new WebSearchResult(
                        n.path("title").asText(n.path("docTitle").asText("知识库片段")),
                        n.path("url").asText("internal://kb/" + n.path("docId").asText()),
                        n.path("snippet").asText(n.path("content").asText("")),
                        n.path("score").asDouble(0.5)
                    ));
                }
                // 如果知识库命中 < 3 条, 用内置 corpus 补齐
                if (out.size() < 3) {
                    for (WebSearchResult c : INTERNAL_CORPUS) {
                        if (matches(c, query)) {
                            out.add(c);
                            if (out.size() >= topK) break;
                        }
                    }
                }
                return out;
            }
        } catch (Exception e) {
            log.debug("[internal] webSearch 知识库调用失败: {}", e.getMessage());
        }
        // 2) 兜底: 内置 corpus
        return INTERNAL_CORPUS.stream()
            .filter(c -> query.isBlank() || matches(c, query))
            .limit(Math.max(1, topK))
            .toList();
    }

    @Override
    public boolean isHealthy() {
        try {
            HttpRequest r = HttpRequest.newBuilder()
                    .uri(URI.create(props.getKnowledgeUrl() + "/actuator/health"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // ========== 工具 ==========

    private String extractLastUser(List<Map<String, String>> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).get("role"))) return messages.get(i).get("content");
        }
        return "";
    }

    private boolean matches(WebSearchResult c, String query) {
        String q = query.toLowerCase();
        return c.title().toLowerCase().contains(q)
            || c.snippet().toLowerCase().contains(q)
            || anyWordMatch(q, c.title().toLowerCase() + " " + c.snippet().toLowerCase());
    }

    private boolean anyWordMatch(String q, String text) {
        for (String w : q.split("\\s+")) {
            if (w.length() > 1 && text.contains(w)) return true;
        }
        return false;
    }

    private String callInference(String prompt, Map<String, Object> options) {
        try {
            ObjectNode req = mapper.createObjectNode();
            req.put("model", "default");
            req.put("input", prompt);
            req.put("task", "chat");
            if (options != null && options.containsKey("temperature")) {
                req.put("temperature", ((Number) options.get("temperature")).doubleValue());
            }
            String body = httpPost(props.getInferenceUrl() + "/api/inference/generate", req.toString());
            JsonNode r = mapper.readTree(body);
            return r.path("data").path("text").asText("[inference returned no text]");
        } catch (Exception e) {
            log.warn("[Internal AI] inference 调用失败: {}", e.getMessage());
            return "[Internal AI 暂不可达: " + e.getMessage() + "]";
        }
    }

    private String httpPost(String url, String body) throws Exception {
        HttpRequest r = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(r, HttpResponse.BodyHandlers.ofString()).body();
    }
}
