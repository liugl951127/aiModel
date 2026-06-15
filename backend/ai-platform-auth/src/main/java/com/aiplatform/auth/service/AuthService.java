package com.aiplatform.auth.service;

import com.aiplatform.auth.dto.LoginRequest;
import com.aiplatform.auth.dto.LoginResponse;
import com.aiplatform.auth.feign.TenantServiceClient;
import com.aiplatform.auth.feign.UserServiceClient;
import com.aiplatform.common.constant.CommonConstants;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.Result;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.common.util.JwtUtils;
import com.aiplatform.starter.redis.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;
    private final UserServiceClient userServiceClient;
    private final TenantServiceClient tenantServiceClient;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 登录前置：按用户名查用户信息 + 该用户可用的公司列表。
     * 之所以分两步，是为了让前端可以"先输用户名 → 拿到公司下拉 → 选完公司 + 输密码 → 登录"，
     * 体验更接近钉钉/飞书的企业登录流程。
     */
    public Map<String, Object> preview(String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "username required");
        }
        Result<Map<String, Object>> resp = userServiceClient.getByUsername(username);
        if (resp == null || resp.getData() == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        Map<String, Object> user = resp.getData();
        boolean isSuperAdmin = "admin".equalsIgnoreCase(username);
        Result<List<Map<String, Object>>> tenantsResp = tenantServiceClient.listByUsername(username);
        List<Map<String, Object>> tenants = tenantsResp == null ? List.of()
                : (tenantsResp.getData() == null ? List.of() : tenantsResp.getData());

        // 给前端一个不泄漏密码的版本
        Map<String, Object> safe = new java.util.LinkedHashMap<>();
        safe.put("userId", user.get("id"));
        safe.put("username", user.get("username"));
        safe.put("nickname", user.get("nickname"));
        safe.put("avatar", user.get("avatar"));
        safe.put("department", user.get("department"));
        safe.put("isSuperAdmin", isSuperAdmin);
        safe.put("roles", isSuperAdmin
                ? java.util.List.of("SUPER_ADMIN", "PLATFORM_ADMIN", "user")
                : java.util.List.of("user"));
        safe.put("tenants", isSuperAdmin
                ? java.util.List.of(java.util.Map.of(
                        "id", 0L, "tenantCode", "ALL", "tenantName", "全部公司（超管）", "role", "super"))
                : tenants);
        return safe;
    }

    public LoginResponse login(LoginRequest req) {
        Result<Map<String, Object>> resp = userServiceClient.getByUsername(req.getUsername());
        if (resp == null || resp.getCode() == null || resp.getCode() != ResultCode.SUCCESS.getCode()) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        Map<String, Object> user = resp.getData();
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        Integer status = (Integer) user.get("status");
        if (status != null && status == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        String stored = (String) user.get("password");
        if (stored == null || !passwordEncoder.matches(req.getPassword(), stored)) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }

        Long userId = ((Number) user.get("id")).longValue();
        String username = (String) user.get("username");
        String department = (String) user.get("department");
        String nickname = (String) user.get("nickname");
        String avatar = (String) user.get("avatar");

        // 超级管理员判定：username = "admin" — 拥有所有租户的最高权限。
        // 业务场景：admin 登录时不需要选公司，AuthService 自动给超级租户 0L。
        // 后台 sys_role 表里有 PLATFORM_ADMIN role，这里用 username 快速判定，
        // 生产可以替换为：查 sys_user_role 关联表。
        boolean isSuperAdmin = "admin".equalsIgnoreCase(username);

        Long tenantId = req.getTenantId();
        String tenantCode = null;
        String tenantName = null;
        if (isSuperAdmin && (tenantId == null || tenantId == 0L)) {
            // 超级管理员：不需选公司，默认拥有所有租户权限
            tenantId = CommonConstants.SUPER_TENANT_ID;
            tenantCode = "ALL";
            tenantName = "全部公司（超级管理员）";
        } else if (tenantId == null) {
            tenantId = user.get("tenantId") == null
                    ? CommonConstants.SUPER_TENANT_ID
                    : ((Number) user.get("tenantId")).longValue();
        } else {
            // 校验该用户确实属于这家公司
            final long finalTenantId = tenantId;
            Result<List<Map<String, Object>>> tr = tenantServiceClient.listByUsername(username);
            if (tr != null && tr.getData() != null) {
                boolean ok = tr.getData().stream().anyMatch(t -> finalTenantId == ((Number) t.get("id")).longValue());
                if (!ok) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "该用户不属于此公司");
                }
                for (Map<String, Object> t : tr.getData()) {
                    if (finalTenantId == ((Number) t.get("id")).longValue()) {
                        tenantCode = (String) t.get("tenantCode");
                        tenantName = (String) t.get("tenantName");
                        break;
                    }
                }
            }
        }

        String accessToken = jwtUtils.generate(userId, username, tenantId, department);
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        redisUtils.set("refresh:" + refreshToken, accessToken, 7 * 24 * 3600);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtils.getExpiration() / 1000)
                .userId(userId)
                .username(username)
                .roles(isSuperAdmin
                        ? java.util.List.of("SUPER_ADMIN", "PLATFORM_ADMIN", "user")
                        : java.util.List.of("user"))
                .nickname(nickname)
                .avatar(avatar)
                .department(department)
                .tenantId(tenantId)
                .tenantCode(tenantCode)
                .tenantName(tenantName)
                .build();
    }

    public void logout(String token) {
        if (token != null) {
            redisUtils.delete("token:" + token);
        }
    }

    public String refresh(String refreshToken) {
        String access = redisUtils.get("refresh:" + refreshToken);
        if (access == null) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }
        return access;
    }
}
