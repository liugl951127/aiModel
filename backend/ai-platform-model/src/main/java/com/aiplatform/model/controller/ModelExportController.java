package com.aiplatform.model.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.model.service.ModelExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model/export")
@RequiredArgsConstructor
public class ModelExportController {

    private final ModelExportService exportService;

    @PostMapping("/{modelId}")
    public Result<String> export(@PathVariable Long modelId) {
        return Result.success(exportService.exportBundle(modelId));
    }
}
