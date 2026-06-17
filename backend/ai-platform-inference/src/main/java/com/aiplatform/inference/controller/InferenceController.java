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

    /**
     * 推理服务健康检查 (供 Dashboard / 监控).
     * 返回服务状态 + 可用模型数 + 启动时间.
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("status", "UP");
        r.put("service", "ai-platform-inference");
        r.put("models", inferenceService.list().size());
        r.put("uptime", System.currentTimeMillis());
        r.put("time", java.time.LocalDateTime.now().toString());
        return Result.success(r);
    }
}
