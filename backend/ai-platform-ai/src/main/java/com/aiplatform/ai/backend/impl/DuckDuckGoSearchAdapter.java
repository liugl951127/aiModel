package com.aiplatform.ai.backend.impl;

import com.aiplatform.ai.backend.AIBackend;
import com.aiplatform.ai.backend.ExternalWebSearchAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * ★ DuckDuckGo 外部联网搜索适配器 (保留, 跟 internal 并存).
 *
 * <p>激活: {@code aiplatform.ai.search-mode=external} (默认 internal, 不联网)</p>
 *
 * <p>走 DuckDuckGo Instant Answer API (公开, 无需 key, 有隐私保护):
 * <pre>
 *   https://api.duckduckgo.com/?q={query}&format=json&no_html=1
 * </pre>
 *
 * <p>注意: 调用会真正发起 HTTPS 请求, 失败/超时自动降级到 internal.</p>
 */
@Slf4j

@ConditionalOnProperty(name = "aiplatform.ai.search-mode", havingValue = "external", matchIfMissing = false)
public class DuckDuckGoSearchAdapter implements ExternalWebSearchAdapter {

    @Value("${aiplatform.ai.external.duckduckgo-endpoint:https://api.duckduckgo.com/}")
    private String endpoint;
    @Value("${aiplatform.ai.external.timeout-ms:4000}")
    private int timeoutMs;
    @Value("${aiplatform.ai.external.enabled:true}")
    private boolean enabled;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String name() { return "duckduckgo"; }

    @Override
    public boolean isAvailable() {
        if (!enabled) return false;
        try {
            HttpRequest r = HttpRequest.newBuilder()
                    .uri(URI.create("https://duckduckgo.com/"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<Void> resp = http.send(r, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<AIBackend.WebSearchResult> search(String query, int topK) throws RuntimeException {
        if (query == null || query.isBlank()) return List.of();
        try {
            String url = endpoint + "?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&format=json&no_html=1&skip_disambig=1";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "AI-Platform/2.0 (internal-use)")
                    .GET().build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("HTTP " + resp.statusCode());
            }
            return parse(resp.body(), topK);
        } catch (Exception e) {
            throw new RuntimeException("DuckDuckGo search failed: " + e.getMessage(), e);
        }
    }

    private List<AIBackend.WebSearchResult> parse(String body, int topK) throws Exception {
        JsonNode root = mapper.readTree(body);
        List<AIBackend.WebSearchResult> results = new ArrayList<>();

        // 抽取 AbstractText (主答案)
        if (root.has("AbstractText") && !root.path("AbstractText").asText().isBlank()) {
            results.add(new AIBackend.WebSearchResult(
                root.path("Heading").asText(query()),
                root.path("AbstractURL").asText(endpoint),
                root.path("AbstractText").asText(),
                0.9
            ));
        }

        // 抽取 RelatedTopics
        JsonNode topics = root.path("RelatedTopics");
        if (topics.isArray()) {
            for (JsonNode t : topics) {
                if (results.size() >= topK) break;
                if (t.has("Text") && !t.path("Text").asText().isBlank()) {
                    String text = t.path("Text").asText();
                    results.add(new AIBackend.WebSearchResult(
                        extractTitle(text),
                        t.path("FirstURL").asText(endpoint),
                        text,
                        0.6
                    ));
                }
            }
        }
        return results;
    }

    private String query() { return ""; }

    private String extractTitle(String text) {
        if (text == null) return "";
        int dot = text.indexOf('.');
        return dot > 0 && dot < 60 ? text.substring(0, dot) : (text.length() > 60 ? text.substring(0, 60) : text);
    }
}
