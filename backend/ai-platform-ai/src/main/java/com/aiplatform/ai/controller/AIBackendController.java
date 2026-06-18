package com.aiplatform.ai.controller;

import com.aiplatform.ai.backend.AIBackend;
import com.aiplatform.ai.backend.AIBackendRouter;
import com.aiplatform.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ★ AI 后端管理 API — 列出/切换后端, 便于运维查看当前用哪个.
 *
 * <p>用法:
 * <ul>
 *   <li>GET  /api/ai/backends        — 列所有可用后端 + 当前激活</li>
 *   <li>GET  /api/ai/health          — 当前后端健康</li>
 *   <li>POST /api/ai/switch/{name}   — 切换后端 (运行时, 不需重启)</li>
 *   <li>POST /api/ai/chat            — 调当前后端 chat (测试用)</li>
 *   <li>POST /api/ai/embed           — 调当前后端 embed (测试用)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIBackendController {

    private final AIBackendRouter router;

    @GetMapping("/backends")
    public Result<Map<String, Object>> list() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("active", router.active().name());
        data.put("healthy", router.active().isHealthy());
        data.put("available", router.availableBackends());
        return Result.success(data);
    }

    @GetMapping("/health")
    public Result<Boolean> health() {
        return Result.success(router.isHealthy());
    }

    @PostMapping("/switch/{name}")
    public Result<String> switchBackend(@PathVariable String name) {
        router.switchTo(name);
        return Result.success("switched to: " + router.active().name());
    }

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody Map<String, Object> req) {
        String system = (String) req.getOrDefault("system", "你是一个有帮助的助手.");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) req.getOrDefault("messages", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) req.getOrDefault("options", Map.of());
        String response = router.chat(system, messages, options);
        return Result.success(response);
    }

    @PostMapping("/embed")
    public Result<float[]> embed(@RequestBody Map<String, Object> req) {
        String text = (String) req.getOrDefault("text", "");
        return Result.success(router.embed(text));
    }

    @PostMapping("/web-search")
    public Result<List<AIBackend.WebSearchResult>> webSearch(@RequestBody Map<String, Object> req) {
        String query = (String) req.getOrDefault("query", "");
        int topK = ((Number) req.getOrDefault("topK", 5)).intValue();
        return Result.success(router.webSearch(query, topK));
    }

    @PostMapping("/rerank")
    public Result<List<AIBackend.RerankResult>> rerank(@RequestBody Map<String, Object> req) {
        String query = (String) req.getOrDefault("query", "");
        @SuppressWarnings("unchecked")
        List<String> candidates = (List<String>) req.getOrDefault("candidates", List.of());
        int topK = ((Number) req.getOrDefault("topK", 3)).intValue();
        return Result.success(router.rerank(query, candidates, topK));
    }
}
