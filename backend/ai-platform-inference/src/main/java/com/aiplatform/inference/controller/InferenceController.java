package com.aiplatform.inference.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.inference.service.InferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inference")
@RequiredArgsConstructor
public class InferenceController {

    private final InferenceService inferenceService;

    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        String modelCode = (String) body.getOrDefault("modelCode", "default");
        String prompt = (String) body.getOrDefault("prompt", "");
        int maxTokens = body.get("maxTokens") == null ? 64 : ((Number) body.get("maxTokens")).intValue();
        float temperature = body.get("temperature") == null ? 0.8f
                : Float.parseFloat(body.get("temperature").toString());
        return Result.success(inferenceService.generate(modelCode, prompt, maxTokens, temperature));
    }

    @GetMapping("/models")
    public Result<Map<String, String>> models() {
        return Result.success(inferenceService.list());
    }
}
