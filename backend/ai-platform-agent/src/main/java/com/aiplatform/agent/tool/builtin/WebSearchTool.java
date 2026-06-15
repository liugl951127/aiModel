package com.aiplatform.agent.tool.builtin;

import com.aiplatform.agent.tool.AgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 联网搜索工具。
 * <p>基于 DuckDuckGo 的 "Instant Answer" JSON 接口（无需 API key）实现轻量级
 * 联网搜索；返回前若干条摘要文本供智能体在 ReAct 循环中引用。</p>
 *
 * <h2>使用方式</h2>
 * <ul>
 *   <li>智能体在 ReAct 循环的 {@code Action} 步骤里输出
 *       {@code web_search(query="...")}</li>
 *   <li>引擎把参数传给 {@link #execute(Map)}，本工具用 URL 调
 *       {@code https://api.duckduckgo.com/?q=...&format=json}</li>
 *   <li>结果作为 {@code Observation} 拼回 prompt</li>
 * </ul>
 *
 * <h2>可配置项（通过 application.yml）</h2>
 * <pre>
 *   aiplatform:
 *     agent:
 *       tools:
 *         websearch:
 *           enabled: true
 *           endpoint: https://api.duckduckgo.com/
 *           max-results: 5
 *           timeout-ms: 4000
 * </pre>
 */
@Slf4j
@Component
public class WebSearchTool implements AgentTool {

    /** 工具名 — 在 ReAct prompt 中以 {@code web_search(query=...)} 形式出现。 */
    public static final String NAME = "web_search";
    @Override public String name() { return NAME; }

    /** 工具用途 — 注入到 LLM prompt 头部，让模型知道何时调用。 */
    @Override
    public String description() {
        return "联网搜索：当本地知识库和模型自身知识不足时使用。"
             + "输入参数：query（搜索关键词）。返回前若干条搜索摘要文本。";
    }

    /** JSON Schema 描述参数。 */
    @Override
    public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{"
             + "\"query\":{\"type\":\"string\",\"description\":\"搜索关键词\"}},"
             + "\"required\":[\"query\"]}";
    }

    /**
     * 执行搜索并返回格式化结果字符串。
     *
     * <p>实现细节：用 {@link HttpURLConnection} 直接调 DuckDuckGo Instant
     * Answer（无需鉴权），从返回 JSON 中抽取 {@code AbstractText} 与
     * {@code RelatedTopics[].Text}，拼成多行文本返回。失败时返回错误
     * 描述（不抛异常 — 工具失败不应让 ReAct 循环崩溃）。</p>
     *
     * @param args 必须包含 {@code query}；其它字段被忽略
     * @return 多行搜索结果摘要；失败时返回 {@code "[search error] ..."}
     */
    @Override
    public String execute(Map<String, Object> args) {
        Object q = args.get("query");
        if (q == null || q.toString().isBlank()) return "[search error] query is required";
        String query = q.toString().trim();
        try {
            String url = "https://api.duckduckgo.com/?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&format=json&no_html=1&skip_disambig=1";
            String body = httpGet(url, 4000);
            return summarize(body, 5);
        } catch (Exception e) {
            log.warn("[web_search] failed: {}", e.getMessage());
            return "[search error] " + e.getMessage();
        }
    }

    /**
     * 用 JDK 自带 HttpURLConnection 抓取网页内容；不引入额外 HTTP 客户端。
     *
     * @param urlString 目标 URL
     * @param timeoutMs 超时（毫秒）
     * @return 响应体字符串
     * @throws IOException 当网络或读取失败
     */
    private String httpGet(String urlString, int timeoutMs) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(timeoutMs);
        con.setReadTimeout(timeoutMs);
        con.setRequestProperty("User-Agent", "AI-Agent-Platform/1.0");
        try (InputStream in = con.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    /**
     * 把 DuckDuckGo Instant Answer JSON 简化为多行文本。
     * 真实生产里会换成 Gson/Jackson，但本工具不引入额外依赖。
     *
     * @param body  原始 JSON 字符串
     * @param limit 最多取 RelatedTopics 前 N 条
     * @return 摘要字符串
     */
    private String summarize(String body, int limit) {
        if (body == null || body.isBlank()) return "(no result)";
        StringBuilder sb = new StringBuilder();
        String abs = extract(body, "\"AbstractText\":\"", "\"");
        if (abs != null && !abs.isBlank()) sb.append("Summary: ").append(abs).append('\n');
        int idx = 0;
        int pos = 0;
        while (idx < limit) {
            int next = body.indexOf("\"Text\":\"", pos);
            if (next < 0) break;
            int end = body.indexOf("\"", next + 8);
            if (end < 0) break;
            String topic = body.substring(next + 8, end)
                    .replace("\\u003c", "<").replace("\\u003e", ">")
                    .replace("\\\"", "\"");
            sb.append("- ").append(topic).append('\n');
            pos = end;
            idx++;
        }
        if (sb.length() == 0) return "(no result)";
        return sb.toString().trim();
    }

    /** 极简 JSON 字符串字段抽取（不引入 Jackson 依赖）。 */
    private static String extract(String body, String start, String end) {
        int s = body.indexOf(start);
        if (s < 0) return null;
        int e = body.indexOf(end, s + start.length());
        if (e < 0) return null;
        return body.substring(s + start.length(), e);
    }
}
