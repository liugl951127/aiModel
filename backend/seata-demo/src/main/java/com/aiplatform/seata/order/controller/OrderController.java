package com.aiplatform.seata.order.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.seata.order.service.AgentInvokeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 全局事务入口。模拟"用户调用一次 ReAct 任务" — 跨 3 个微服务原子完成。
 */
@RestController
@RequestMapping("/api/seata/order")
@RequiredArgsConstructor
public class OrderController {

    private final AgentInvokeService agentInvokeService;

    @PostMapping("/success")
    public Result<AgentInvokeService.InvokeResult> success(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        String agentCode = (String) body.getOrDefault("agentCode", "A-DEFAULT01");
        String prompt = (String) body.getOrDefault("prompt", "hi");
        Long tokens = ((Number) body.get("tokens")).longValue();
        return Result.success(agentInvokeService.invokeSuccess(userId, agentCode, prompt, tokens));
    }

    @PostMapping("/rollback")
    public Result<AgentInvokeService.InvokeResult> rollback(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        String agentCode = (String) body.getOrDefault("agentCode", "A-DEFAULT01");
        String prompt = (String) body.getOrDefault("prompt", "hi");
        Long tokens = ((Number) body.get("tokens")).longValue();
        return Result.success(agentInvokeService.invokeRollback(userId, agentCode, prompt, tokens));
    }
}
