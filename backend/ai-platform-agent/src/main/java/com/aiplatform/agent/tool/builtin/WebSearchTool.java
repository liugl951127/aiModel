package com.aiplatform.agent.tool.builtin;

import com.aiplatform.agent.tool.AgentTool;
import com.aiplatform.ai.backend.AIBackend;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ★ 本地联网搜索工具 (替代原 DuckDuckGo 外部 API).
 *
 * <p>本工具不再调任何外部服务, 而是通过 {@link AIBackend} 接口走本地实现.
 * 默认走 {@code MockAIBackend.webSearch()} (内置 corpus 关键词匹配), 也可
 * 配 {@code aiplatform.ai.backend=onnx|ollama|http} 切换.</p>
 *
 * <h2>改造前后对比</h2>
 * <table border="1">
 *   <caption>原实现 vs 本地实现</caption>
 *   <tr><th>原</th><td>HTTPS → api.duckduckgo.com (公网, 不稳定, 隐私风险)</td></tr>
 *   <tr><th>现在</th><td>本地 AIBackend.webSearch() (内网, 0 联网, 0 风险)</td></tr>
 * </table>
 *
 * <h2>Agent ReAct 调用</h2>
 * 智能体在 ReAct 循环的 {@code Action} 步骤里输出
 * {@code web_search(query="...")} → 引擎把参数传给本工具
 * → 返回前若干条本地检索结果拼回 prompt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool implements AgentTool {

    public static final String NAME = "web_search";

    private final AIBackend aiBackend;

    @PostConstruct
    public void warmup() {
        log.info("[web_search] 工具初始化完成, AI 后端: {}", aiBackend.name());
    }

    @Override public String name() { return NAME; }

    @Override
    public String description() {
        return "本地联网搜索: 当本地知识库和模型自身知识不足时使用."
             + " (替代原 DuckDuckGo 外部 API, 完全自包含.)"
             + " 输入参数: query (搜索关键词). 返回前若干条搜索摘要文本.";
    }

    @Override
    public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{"
             + "\"query\":{\"type\":\"string\",\"description\":\"搜索关键词\"},"
             + "\"topK\":{\"type\":\"integer\",\"description\":\"返回前 N 条, 默认 5\"}},"
             + "\"required\":[\"query\"]}";
    }

    @Override
    public String execute(Map<String, Object> args) {
        Object q = args.get("query");
        if (q == null || q.toString().isBlank()) return "[search error] query is required";
        String query = q.toString().trim();
        int topK = 5;
        if (args.get("topK") instanceof Number n) topK = Math.max(1, n.intValue());

        try {
            List<AIBackend.WebSearchResult> results = aiBackend.webSearch(query, topK);
            if (results.isEmpty()) {
                return "[local search] 没有匹配结果. (后端: " + aiBackend.name() + ")";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("[local search · 后端: ").append(aiBackend.name()).append("] ").append(results.size()).append(" 条:\n\n");
            int i = 1;
            for (AIBackend.WebSearchResult r : results) {
                sb.append(i++).append(". ").append(r.title()).append("\n")
                  .append("   ").append(r.url()).append("\n")
                  .append("   ").append(r.snippet()).append("\n")
                  .append("   (score: ").append(String.format("%.2f", r.score())).append(")\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[web_search] 失败: {}", e.getMessage());
            return "[search error] " + e.getMessage();
        }
    }
}
