package com.aiplatform.auth.feign;

import com.aiplatform.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "ai-platform-user", fallbackFactory = UserServiceFallback.class)
public interface UserServiceClient {

    @GetMapping("/api/user/feign/by-username")
    Result<Map<String, Object>> getByUsername(@RequestParam("username") String username);
}
