package com.aiplatform.agent.feign;

import com.aiplatform.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "ai-platform-inference", fallbackFactory = InferenceServiceFallback.class)
public interface InferenceServiceClient {

    @PostMapping("/api/inference/generate")
    Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body);
}
