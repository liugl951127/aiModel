package com.aiplatform.inference.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.inference.service.InferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OpenAI-style chat completion API.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final InferenceService inferenceService;

    @PostMapping("/completions")
    public Result<Map<String, Object>> completions(@RequestBody Map<String, Object> body) {
        String modelCode = (String) body.getOrDefault("model", "default");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> messages = (java.util.List<Map<String, Object>>) body.get("messages");
        String prompt = buildPrompt(messages);
        int maxTokens = body.get("max_tokens") == null ? 256
                : ((Number) body.get("max_tokens")).intValue();
        Double temp = body.get("temperature") == null ? 0.7
                : Double.parseDouble(body.get("temperature").toString());
        return Result.success(inferenceService.generate(modelCode, prompt, maxTokens, temp.floatValue()));
    }

    private String buildPrompt(java.util.List<Map<String, Object>> messages) {
        if (messages == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> m : messages) {
            Object role = m.get("role");
            Object content = m.get("content");
            sb.append(role).append(": ").append(content).append("\n");
        }
        sb.append("assistant:");
        return sb.toString();
    }
}
