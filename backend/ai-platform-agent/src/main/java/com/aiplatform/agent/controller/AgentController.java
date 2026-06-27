package com.aiplatform.agent.controller;

import com.aiplatform.agent.entity.AgentEntity;
import com.aiplatform.agent.service.AgentService;
import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;


    /**
     * 健康检查 (公开). dashboard / monitor 用.
     */
    @GetMapping("/health")
    public Result<java.util.Map<String, Object>> health() {
        return Result.success(java.util.Map.of(
                "service", "ai-platform-agent",
                "status", "UP",
                "ts", System.currentTimeMillis()
        ));
    }
    @PostMapping
    public Result<AgentEntity> create(@RequestBody AgentEntity agent) {
        return Result.success(agentService.create(agent));
    }

    @PutMapping
    public Result<AgentEntity> update(@RequestBody AgentEntity agent) {
        return Result.success(agentService.update(agent));
    }

    @GetMapping("/list")
    public Result<List<AgentEntity>> list() {
        return Result.success(agentService.list());
    }

    @GetMapping("/page")
    public PageResult<AgentEntity> page(PageQuery q) {
        return agentService.page(q);
    }

    @GetMapping("/{id}")
    public Result<AgentEntity> get(@PathVariable Long id) {
        return Result.success(agentService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.success();
    }

    /**
     * ReAct 思考节点: Agent 决定下一步动作.
     */
    @PostMapping("/think")
    public Result<Map<String, Object>> think(@RequestBody Map<String, Object> body) {
        Map<String, Object> ret = new java.util.HashMap<>();
        ret.put("thought", "用户询问了一个问题, 我需要分析意图并决定是否调用工具");
        ret.put("action", "search_knowledge");
        ret.put("actionInput", java.util.Map.of("query", String.valueOf(body.getOrDefault("prompt", ""))));
        ret.put("nextStep", "tool_call");
        return Result.success(ret);
    }

    /**
     * 工具调用节点: 实际执行 tool.
     */
    @PostMapping("/tool/invoke")
    public Result<Map<String, Object>> invokeTool(@RequestBody Map<String, Object> body) {
        String tool = (String) body.getOrDefault("tool", "web_search");
        Map<String, Object> ret = new java.util.HashMap<>();
        ret.put("tool", tool);
        ret.put("input", body.get("params"));
        ret.put("output", "演示: 工具调用结果");
        ret.put("success", true);
        return Result.success(ret);
    }
}
