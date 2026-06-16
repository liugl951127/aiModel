package com.aiplatform.auth.feign;

import com.aiplatform.common.result.Result;
import com.aiplatform.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class UserServiceFallback implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        log.error("[FEIGN] user-service fallback: {}", cause.getMessage());
        return new UserServiceClient() {
            @Override
            public Result<Map<String, Object>> getByUsername(String username) {
                return Result.fail(ResultCode.FAIL.getCode(), "用户服务不可用: " + cause.getMessage());
            }

            @Override
            public Result<Map<String, Object>> create(Map<String, Object> body) {
                return Result.fail(ResultCode.FAIL.getCode(), "用户服务不可用: " + cause.getMessage());
            }
        };
    }
}
