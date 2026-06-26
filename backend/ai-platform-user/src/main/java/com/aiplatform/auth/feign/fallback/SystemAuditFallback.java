package com.aiplatform.auth.feign.fallback;

import com.aiplatform.auth.feign.SystemAuditClient;
import com.aiplatform.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class SystemAuditFallback implements FallbackFactory<SystemAuditClient> {
    @Override
    public SystemAuditClient create(Throwable cause) {
        log.warn("[FEIGN] SystemAuditClient fallback: {}", cause.getMessage());
        return body -> Result.success();
    }
}
