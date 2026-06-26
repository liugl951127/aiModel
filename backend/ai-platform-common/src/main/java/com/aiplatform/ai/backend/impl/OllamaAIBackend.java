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
 * ★ Ollama 后端 — 调本地 Ollama 服务 (兼容 llama.cpp / qwen2.5 / llama3 等开源模型).
 *
 * <p>激活: {@code aiplatform.ai.backend=ollama}</p>
 * <pre>
 *   aiplatform:
 *     ai:
 *       backend: ollama
 *       ollama:
 *         base-url: http://127.0.0.1:11434
 *         chat-model: qwen2.5:7b
 *         embed-model: nomic-embed-text
 *         timeout-ms: 30000
 * </pre>
 *
 * <p>前置: 跑 {@code ollama serve}, 拉模型: {@code ollama pull qwen2.5:7b}</p>
 */
@Slf4j

@ConditionalOnProperty(name = "aiplatform.ai.backend", havingValue = "ollama")
public class OllamaAIBackend implements AIBackend {

    @Configuration
    @ConfigurationProperties(prefix = "aiplatform.ai.ollama")
    public static class Props {
        private String baseUrl = "http://127.0.0.1:11434";
        private String chatModel = "qwen2.5:7b";
        private String embedModel = "nomic-embed-text";
        private int timeoutMs = 30000;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getChatModel() { return chatModel; }
        public void setChatModel(String v) { this.chatModel = v; }
        public String getEmbedModel() { return embedModel; }
        public void setEmbedModel(String v) { this.embedModel = v; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int v) { this.timeoutMs = v; }
    }

    private final Props props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final MockAIBackend mockFallback = new MockAIBackend();

    public OllamaAIBackend(Props props) {
        this.props = props;
    }

    @Override
    public String name() { return "ollama"; }

    @Override
    public String chat(String system, List<Map<String, String>> messages, Map<String, Object> options) {
        try {
            // Ollama /api/chat
            ObjectNode req = mapper.createObjectNode();
            req.put("model", props.getChatModel());
            req.put("stream", false);
            ArrayNode msgs = req.putArray("messages");
            if (system != null && !system.isBlank()) {
                msgs.addObject().put("role", "system").put("content", system);
            }
            for (Map<String, String> m : messages) {
                msgs.addObject().put("role", m.get("role")).put("content", m.get("content"));
            }
            String body = httpPost("/api/chat", req.toString());
            JsonNode r = mapper.readTree(body);
            return r.path("message").path("content").asText("[empty]");
        } catch (Exception e) {
            log.warn("[Ollama] chat 失败: {}", e.getMessage());
            return mockFallback.chat(system, messages, options);
        }
    }

    @Override
    public void chatStream(String system, List<Map<String, String>> messages,
                           Map<String, Object> options, java.util.function.Consumer<String> onChunk) {
        try {
            ObjectNode req = mapper.createObjectNode();
            req.put("model", props.getChatModel());
            req.put("stream", true);
            ArrayNode msgs = req.putArray("messages");
            if (system != null && !system.isBlank()) {
                msgs.addObject().put("role", "system").put("content", system);
            }
            for (Map<String, String> m : messages) {
                msgs.addObject().put("role", m.get("role")).put("content", m.get("content"));
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + "/api/chat"))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(req.toString()))
                    .build();
            http.send(request, HttpResponse.BodyHandlers.ofLines())
                .body()
                .forEach(line -> {
                    if (line.isBlank()) return;
                    try {
                        JsonNode n = mapper.readTree(line);
                        String content = n.path("message").path("content").asText("");
                        if (!content.isEmpty()) onChunk.accept(content);
                    } catch (Exception ignored) {}
                });
        } catch (Exception e) {
            log.warn("[Ollama] chatStream 失败, 降级为非流式: {}", e.getMessage());
            try {
                String full = chat(system, messages, options);
                if (full != null && !full.isEmpty()) onChunk.accept(full);
            } catch (Exception ex) {
                log.error("[Ollama] fallback chat 也失败: {}", ex.getMessage());
            }
        }
    }

    @Override
    public float[] embed(String text) {
        try {
            ObjectNode req = mapper.createObjectNode();
            req.put("model", props.getEmbedModel());
            req.put("prompt", text == null ? "" : text);
            String body = httpPost("/api/embeddings", req.toString());
            JsonNode r = mapper.readTree(body);
            JsonNode emb = r.path("embedding");
            float[] v = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) v[i] = (float) emb.get(i).asDouble();
            return v;
        } catch (Exception e) {
            log.warn("[Ollama] embed 失败: {}", e.getMessage());
            return mockFallback.embed(text);
        }
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> candidates, int topK) {
        // Ollama 暂不直接做 rerank, 用 embedding cosine 近似
        float[] qv = embed(query);
        List<RerankResult> results = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            float[] cv = embed(candidates.get(i));
            float score = cosine(qv, cv);
            results.add(new RerankResult(i, score));
        }
        results.sort((a, b) -> Float.compare(b.score(), a.score()));
        return results.subList(0, Math.min(topK, results.size()));
    }

    @Override
    public List<WebSearchResult> webSearch(String query, int topK) {
        return mockFallback.webSearch(query, topK);
    }

    @Override
    public boolean isHealthy() {
        try {
            HttpRequest r = HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + "/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET().build();
            HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String httpPost(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + path))
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static float cosine(float[] a, float[] b) {
        float dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        if (na == 0 || nb == 0) return 0;
        return dot / (float) (Math.sqrt(na) * Math.sqrt(nb));
    }
}
