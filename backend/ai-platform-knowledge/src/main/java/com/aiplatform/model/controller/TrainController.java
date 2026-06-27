package com.aiplatform.model.controller;

import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.common.result.Result;
import com.aiplatform.model.entity.TrainJob;
import com.aiplatform.model.service.TrainJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/train")
@RequiredArgsConstructor
public class TrainController {

    private final TrainJobService trainService;


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
    @PostMapping("/submit")
    public Result<TrainJob> submit(@RequestBody TrainJob job) {
        return Result.success(trainService.submit(job));
    }

    @GetMapping("/page")
    public PageResult<TrainJob> page(PageQuery q) {
        return trainService.page(q);
    }

    @GetMapping("/{id}")
    public Result<TrainJob> get(@PathVariable Long id) {
        return Result.success(trainService.get(id));
    }
}
