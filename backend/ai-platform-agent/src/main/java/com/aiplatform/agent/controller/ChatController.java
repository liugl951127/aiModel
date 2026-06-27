package com.aiplatform.agent.controller;

import com.aiplatform.agent.engine.AgentRunResult;
import com.aiplatform.agent.entity.Message;
import com.aiplatform.agent.service.AgentOrchestrator;
import com.aiplatform.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class ChatController {

    private final AgentOrchestrator orchestrator;

    @PostMapping("/chat")
    @SuppressWarnings("unchecked")
    public Result<AgentRunResult> chat(@RequestBody Map<String, Object> body) {
        // ★ v3.x 修复: 前端可能传 string "123", 安全转 Long
        Long agentId = toLong(body.get("agentId"));
        String sessionId = (String) body.get("sessionId");
        String input = (String) body.get("input");
        return Result.success(orchestrator.chat(agentId, sessionId, input));
    }

    /** 兼容 Number / 数字 String / null → Long */
    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    @GetMapping("/history")
    public Result<List<Message>> history(@RequestParam String sessionId) {
        return Result.success(orchestrator.history(sessionId));
    }

    /**
     * Agent 调用日志 (DB, 重启不丢). 给前端 /api/agent/invoke-logs 用.
     */
    @GetMapping("/invoke-logs")
    public Result<List<com.aiplatform.agent.entity.AgentInvokeLogEntity>> invokeLogs(
            @RequestParam(required = false) Long agentId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(orchestrator.listInvokeLogs(agentId, limit));
    }
}
