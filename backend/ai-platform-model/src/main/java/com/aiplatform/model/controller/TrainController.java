package com.aiplatform.model.controller;

import com.aiplatform.common.entity.PageQuery;
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
