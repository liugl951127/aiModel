package com.aiplatform.agent.controller;

import com.aiplatform.agent.entity.ToolEntity;
import com.aiplatform.agent.service.ToolService;
import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tool")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;


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
    public Result<ToolEntity> create(@RequestBody ToolEntity tool) {
        return Result.success(toolService.create(tool));
    }

    @GetMapping("/list")
    public Result<List<ToolEntity>> list() {
        return Result.success(toolService.list());
    }

    @GetMapping("/page")
    public PageResult<ToolEntity> page(PageQuery q) {
        return toolService.page(q);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        toolService.delete(id);
        return Result.success();
    }
}
