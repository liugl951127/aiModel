package com.aiplatform.auth.feign;

import com.aiplatform.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TenantServiceFallback implements FallbackFactory<TenantServiceClient> {

    @Override
    public TenantServiceClient create(Throwable cause) {
        log.warn("[FEIGN] TenantServiceClient fallback: {}", cause.getMessage());
        return new TenantServiceClient() {
            @Override
            public Result<List<Map<String, Object>>> listByUsername(String username) {
                return Result.success(List.of());
            }

            @Override
            public Result<Map<String, Object>> byId(Long id) {
                log.warn("[FEIGN] byId({}) fallback", id);
                return Result.success(null);
            }
        };
    }
}
