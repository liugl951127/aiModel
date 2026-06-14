package com.aiplatform.agent.controller;

import com.aiplatform.agent.entity.ToolEntity;
import com.aiplatform.agent.service.ToolService;
import com.aiplatform.common.entity.PageQuery;
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
