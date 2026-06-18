package com.aiplatform.agent.tool.builtin;

import com.aiplatform.agent.tool.AgentTool;
import com.aiplatform.ai.backend.AIBackend;
import com.aiplatform.ai.backend.AIBackendSwitcher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ★ 联网搜索工具 (内部实现, 外部接口保留).
 *
 * <p>本工具默认走 <b>内部</b> (本系统知识库 + 内置 corpus, 0 联网, 安全).
 * 配 {@code aiplatform.ai.search-mode=external} 可切到 <b>外部</b>
 * (DuckDuckGo / Bing / Google CSE, 联网, 查最新信息).</p>
 *
 * <h2>双轨设计</h2>
 * <ul>
 *   <li><b>internal</b> (默认): 走 AIBackendSwitcher → 调本系统 internal 后端 → 查知识库</li>
 *   <li><b>external</b>: 走 AIBackendSwitcher → 调 DuckDuckGoSearchAdapter → 真正联网</li>
 * </ul>
 *
 * <p>业务侧 (智能体) 只跟本工具交互, 不感知具体后端.</p>
 *
 * <h2>Agent ReAct 调用</h2>
 * 智能体在 ReAct 循环的 {@code Action} 步骤里输出
 * {@code web_search(query="...")} → 引擎把参数传给本工具
 * → 返回前若干条检索结果拼回 prompt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool implements AgentTool {

    public static final String NAME = "web_search";

    private final AIBackendSwitcher switcher;

    @PostConstruct
    public void warmup() {
        log.info("[web_search] 工具初始化完成, chat={}, search-mode={}",
                switcher.currentChatBackend(), switcher.currentSearchMode());
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
            List<AIBackend.WebSearchResult> results = switcher.webSearch(query, topK);
            String mode = switcher.currentSearchMode();
            if (results.isEmpty()) {
                return "[search · " + mode + "] 没有匹配结果. (chat-backend=" + switcher.currentChatBackend() + ")";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("[search · ").append(mode).append(" · chat=")
              .append(switcher.currentChatBackend()).append("] ")
              .append(results.size()).append(" 条:\n\n");
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
