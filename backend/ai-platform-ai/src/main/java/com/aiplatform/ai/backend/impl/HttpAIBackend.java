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
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * ★ HTTP 后端 — 通用 OpenAI-compatible API 客户端 (完全可选, 用于想要接入付费模型时).
 *
 * <p>兼容:
 * <ul>
 *   <li>OpenAI (api.openai.com)</li>
 *   <li>Anthropic (经代理)</li>
 *   <li>DeepSeek / 通义千问 / 智谱 GLM / 百度千帆 / 自建 vLLM</li>
 * </ul>
 *
 * <p>激活: {@code aiplatform.ai.backend=http}</p>
 * <pre>
 *   aiplatform:
 *     ai:
 *       backend: http
 *       http:
 *         base-url: https://api.deepseek.com/v1
 *         api-key: ${DEEPSEEK_API_KEY}
 *         chat-model: deepseek-chat
 *         embed-model: text-embedding-3-small
 * </pre>
 *
 * <p>注意: 用了这个后端, 就有外部联网. 默认 NOT activated.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "aiplatform.ai.backend", havingValue = "http")
public class HttpAIBackend implements AIBackend {

    @Configuration
    @ConfigurationProperties(prefix = "aiplatform.ai.http")
    public static class Props {
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String chatModel = "gpt-3.5-turbo";
        private String embedModel = "text-embedding-3-small";
        private int timeoutMs = 30000;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
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

    public HttpAIBackend(Props props) { this.props = props; }

    @Override public String name() { return "http"; }

    @Override
    public String chat(String system, List<Map<String, String>> messages, Map<String, Object> options) {
        try {
            ObjectNode req = mapper.createObjectNode();
            req.put("model", props.getChatModel());
            ArrayNode msgs = req.putArray("messages");
            if (system != null && !system.isBlank()) {
                msgs.addObject().put("role", "system").put("content", system);
            }
            for (Map<String, String> m : messages) {
                msgs.addObject().put("role", m.get("role")).put("content", m.get("content"));
            }
            if (options != null) {
                if (options.containsKey("temperature")) req.put("temperature", ((Number) options.get("temperature")).doubleValue());
                if (options.containsKey("max_tokens")) req.put("max_tokens", ((Number) options.get("max_tokens")).intValue());
            }
            String body = httpPost("/chat/completions", req.toString());
            JsonNode r = mapper.readTree(body);
            return r.path("choices").path(0).path("message").path("content").asText("[empty]");
        } catch (Exception e) {
            log.warn("[HTTP-AI] chat 失败: {}", e.getMessage());
            return mockFallback.chat(system, messages, options);
        }
    }

    @Override
    public float[] embed(String text) {
        try {
            ObjectNode req = mapper.createObjectNode();
            req.put("model", props.getEmbedModel());
            req.put("input", text == null ? "" : text);
            String body = httpPost("/embeddings", req.toString());
            JsonNode r = mapper.readTree(body);
            JsonNode data = r.path("data").path(0).path("embedding");
            float[] v = new float[data.size()];
            for (int i = 0; i < data.size(); i++) v[i] = (float) data.get(i).asDouble();
            return v;
        } catch (Exception e) {
            log.warn("[HTTP-AI] embed 失败: {}", e.getMessage());
            return mockFallback.embed(text);
        }
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> candidates, int topK) {
        // OpenAI 没原生 rerank, 用 embedding cosine
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
        return props.getApiKey() != null && !props.getApiKey().isBlank();
    }

    private String httpPost(String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + path))
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .header("Content-Type", "application/json");
        if (!props.getApiKey().isBlank()) b.header("Authorization", "Bearer " + props.getApiKey());
        b.POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static float cosine(float[] a, float[] b) {
        float dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        if (na == 0 || nb == 0) return 0;
        return dot / (float) (Math.sqrt(na) * Math.sqrt(nb));
    }
}
