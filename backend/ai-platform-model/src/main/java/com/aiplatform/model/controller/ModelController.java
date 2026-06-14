package com.aiplatform.model.controller;

import com.aiplatform.common.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.common.result.Result;
import com.aiplatform.model.entity.ModelRegistry;
import com.aiplatform.model.service.ModelRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
