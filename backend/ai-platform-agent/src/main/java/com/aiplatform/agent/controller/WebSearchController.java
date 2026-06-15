package com.aiplatform.agent.controller;

import com.aiplatform.agent.tool.AgentTool;
import com.aiplatform.agent.tool.ToolRegistry;
import com.aiplatform.agent.tool.builtin.WebSearchTool;
import com.aiplatform.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 联网搜索演示接口。
 *
 * <p>把 {@link WebSearchTool} 通过 REST 暴露出来，方便手动 curl 验证（不需要
 * 启动整套 ReAct 循环 + LLM）。生产场景下，调用方是智能体引擎本身，不是
 * HTTP 客户端。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   curl 'http://localhost:9004/api/web/search?query=2025%20AI%E5%B8%AE%E6%89%8B'
 *   curl http://localhost:9004/api/web/tools
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class WebSearchController {

    private final ToolRegistry toolRegistry;

    /** 执行联网搜索。 */
    @GetMapping("/search")
    public Result<Map<String, Object>> search(@RequestParam("query") String query) {
        AgentTool tool = toolRegistry.get(WebSearchTool.NAME);
        if (tool == null) {
            return Result.fail(404, "web_search tool not registered");
        }
        long t0 = System.currentTimeMillis();
        String output = tool.execute(Map.of("query", query));
        long elapsedMs = System.currentTimeMillis() - t0;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("tool", WebSearchTool.NAME);
        data.put("output", output);
        data.put("elapsedMs", elapsedMs);
        data.put("preview", output.length() > 240 ? output.substring(0, 240) + "…" : output);
        return Result.success(data);
    }

    /** 列出所有可用工具（含 web_search），用于前端调试面板。 */
    @GetMapping("/tools")
    public Result<List<Map<String, String>>> tools() {
        List<Map<String, String>> list = toolRegistry.all().stream()
                .map(t -> Map.of(
                        "name", t.name(),
                        "description", t.description(),
                        "schema", t.parametersSchema()))
                .toList();
        return Result.success(list);
    }
}
