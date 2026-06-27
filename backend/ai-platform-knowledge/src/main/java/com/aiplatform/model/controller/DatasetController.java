package com.aiplatform.model.controller;

import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.common.result.Result;
import com.aiplatform.model.entity.ModelDataset;
import com.aiplatform.model.service.DatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dataset")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;


    /**
     * 健康检查 (公开). dashboard / monitor 用.
     */
    @GetMapping("/health")
    public Result<java.util.Map<String, Object>> health() {
        return Result.success(java.util.Map.of(
                "service", "ai-platform-knowledge",
                "status", "UP",
                "ts", System.currentTimeMillis()
        ));
    }
    @PostMapping
    public Result<ModelDataset> create(@RequestBody ModelDataset ds) {
        return Result.success(datasetService.create(ds));
    }

    @GetMapping("/page")
    public PageResult<ModelDataset> page(PageQuery q) {
        return datasetService.page(q);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        datasetService.delete(id);
        return Result.success();
    }
}
