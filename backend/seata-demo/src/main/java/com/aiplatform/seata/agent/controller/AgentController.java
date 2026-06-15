package com.aiplatform.seata.agent.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.seata.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent service HTTP 端点。
 */
@RestController
@RequestMapping("/api/seata/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/log")
    public Result<Long> log(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        String agentCode = (String) body.get("agentCode");
        String prompt = (String) body.get("prompt");
        String response = (String) body.get("response");
        Long tokens = ((Number) body.get("tokens")).longValue();
        Boolean success = (Boolean) body.getOrDefault("success", Boolean.TRUE);
        return Result.success(agentService.log(userId, agentCode, prompt, response, tokens, success));
    }
}
