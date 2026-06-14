package com.aiplatform.agent.controller;

import com.aiplatform.agent.entity.AgentEntity;
import com.aiplatform.agent.service.AgentService;
import com.aiplatform.common.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

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
}
