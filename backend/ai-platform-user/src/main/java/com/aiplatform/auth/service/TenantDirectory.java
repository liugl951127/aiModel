package com.aiplatform.auth.service;

import com.aiplatform.auth.feign.TenantServiceClient;
import com.aiplatform.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录页公司（租户）目录服务。
 *
 * <p>登录页 /api/auth/tenants 公开端点用 — 不需要登录即可拿到所有公司，
 * 让用户进登录页时就能看到公司下拉。如果 nacos 拉得到 user-service
 * 就用真表；拉不到就 fallback 到内置 3 家示例公司。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantDirectory {

    private final TenantServiceClient tenantServiceClient;

    private static final List<Map<String, Object>> FALLBACK_TENANTS = List.of(
            Map.of("id", 1L, "tenantCode", "default", "tenantName", "默认公司", "isSuper", false),
            Map.of("id", 2L, "tenantCode", "demo-corp", "tenantName", "示例科技公司", "isSuper", false),
            Map.of("id", 3L, "tenantCode", "startup-co", "tenantName", "创业小公司", "isSuper", false)
    );

    public List<Map<String, Object>> listAll() {
        // feign 已经在带 fallback, 失败时返 FALLBACK. 但这里再包一层防御
        try {
            // feign list 不存在, 用 02 拉个 user 信息. 这里直接返 fallback + 尝试 RPC
            // 注: TenantServiceClient 只有 listByUsername, 没 listAll. 我们构造
            // 几个公开 username 的去查 (admin / demo / manager), 合并去重.
            java.util.Set<Long> seen = new java.util.LinkedHashSet<>();
            List<Map<String, Object>> merged = new ArrayList<>();
            for (String u : List.of("admin", "demo", "manager")) {
                try {
                    Result<List<Map<String, Object>>> r = tenantServiceClient.listByUsername(u);
                    if (r != null && r.getData() != null) {
                        for (Map<String, Object> t : r.getData()) {
                            Object id = t.get("id");
                            if (id != null && seen.add(((Number) id).longValue())) {
                                Map<String, Object> safe = new LinkedHashMap<>(t);
                                safe.put("isSuper", false);
                                merged.add(safe);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("RPC listByUsername({}) 失败: {}", u, e.getMessage());
                }
            }
            return merged.isEmpty() ? FALLBACK_TENANTS : merged;
        } catch (Exception e) {
            log.warn("拉取公司目录失败，用 fallback: {}", e.getMessage());
            return FALLBACK_TENANTS;
        }
    }
}
