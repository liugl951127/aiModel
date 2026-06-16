package com.aiplatform.auth.feign;

import com.aiplatform.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign 客户端：登录审计 (system-service).
 */
@FeignClient(name = "ai-platform-system", fallbackFactory = com.aiplatform.auth.feign.fallback.SystemAuditFallback.class)
public interface SystemAuditClient {
    @PostMapping("/api/audit/login/feign/record")
    Result<Void> record(@RequestBody Map<String, Object> body);
}
