package com.aiplatform.inference.service;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.inference.model.MiniGptModel;
import com.aiplatform.inference.registry.ModelRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InferenceService {

    private final ModelRegistry registry;

    public Map<String, Object> generate(String modelCode, String prompt, int maxTokens, float temperature) {
        MiniGptModel m = registry.get(modelCode);
        if (m == null) {
            throw new BusinessException(ResultCode.MODEL_NOT_FOUND, "模型 " + modelCode + " 未加载");
        }
        long start = System.currentTimeMillis();
        String text = m.generate(prompt == null ? "" : prompt,
                Math.max(1, Math.min(maxTokens, 1024)),
                Math.max(0.1f, temperature));
        Map<String, Object> out = new HashMap<>();
        out.put("text", text);
        out.put("model", modelCode);
        out.put("elapsedMs", System.currentTimeMillis() - start);
        out.put("tokens", Math.min(maxTokens, 1024));
        return out;
    }

    public Map<String, String> list() {
        return registry.summary();
    }
}
