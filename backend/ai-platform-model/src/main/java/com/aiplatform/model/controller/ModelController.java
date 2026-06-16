package com.aiplatform.model.controller;

import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.common.result.Result;
import com.aiplatform.model.entity.ModelRegistry;
import com.aiplatform.model.service.ModelRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
public class ModelController {

    private final ModelRegistryService modelService;

    @PostMapping
    public Result<ModelRegistry> create(@RequestBody ModelRegistry model) {
        return Result.success(modelService.register(model));
    }

    @GetMapping("/list")
    public Result<List<ModelRegistry>> list() {
        return Result.success(modelService.list());
    }

    @GetMapping("/page")
    public PageResult<ModelRegistry> page(PageQuery q) {
        return modelService.page(q);
    }

    @GetMapping("/{id}")
    public Result<ModelRegistry> get(@PathVariable Long id) {
        return Result.success(modelService.getById(id));
    }

    @PutMapping
    public Result<ModelRegistry> update(@RequestBody ModelRegistry model) {
        return Result.success(modelService.update(model));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        modelService.delete(id);
        return Result.success();
    }

    /** 同一 modelCode 下所有版本 */
    @GetMapping("/versions/{modelCode}")
    public Result<List<ModelRegistry>> listVersions(@PathVariable String modelCode) {
        return Result.success(modelService.listVersions(modelCode));
    }

    /** 激活指定版本 (其它同 modelCode 归档) */
    @PostMapping("/{id}/activate")
    public Result<ModelRegistry> activate(@PathVariable Long id) {
        return Result.success(modelService.activate(id));
    }

    /** 比较两个版本 */
    @GetMapping("/compare")
    public Result<Map<String, Object>> compare(@RequestParam Long a, @RequestParam Long b) {
        return Result.success(modelService.compare(a, b));
    }

    /** 统计 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(modelService.stats());
    }

    /** 新增版本 (沿用 modelCode) */
    @PostMapping("/{modelCode}/new-version")
    public Result<ModelRegistry> newVersion(@PathVariable String modelCode, @RequestBody ModelRegistry model) {
        model.setModelCode(modelCode);
        if (model.getVersion() == null) model.setVersion("v0.1.0");
        if (model.getStatus() == null) model.setStatus("draft");
        return Result.success(modelService.register(model));
    }
}
