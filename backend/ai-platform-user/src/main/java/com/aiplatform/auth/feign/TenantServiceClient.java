package com.aiplatform.auth.feign;

import com.aiplatform.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Feign 客户端: 从 user-service 拉公司 (租户) 列表 / 详情.
 *
 * <p>登录流程用 — 用户先输用户名, auth 服务去 user-service 拿"该用户名
 * 属于哪些公司", 前端弹出公司下拉. 租户隔离在公司级, 不下沉到部门.</p>
 */
@FeignClient(name = "ai-platform-user", fallbackFactory = TenantServiceFallback.class)
public interface TenantServiceClient {

    @GetMapping("/api/tenant/feign/by-username")
    Result<List<Map<String, Object>>> listByUsername(@RequestParam("username") String username);

    /**
     * 按 ID 查租户详情 (注册时校验公司是否存在).
     */
    @GetMapping("/api/tenant/feign/{id}")
    Result<Map<String, Object>> byId(@PathVariable("id") Long id);
}
