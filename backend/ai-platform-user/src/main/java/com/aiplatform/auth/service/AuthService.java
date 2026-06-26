package com.aiplatform.auth.service;

import com.aiplatform.auth.dto.LoginRequest;
import com.aiplatform.auth.dto.LoginResponse;
import com.aiplatform.auth.feign.SystemAuditClient;
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
    private final SystemAuditClient systemAuditClient;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 开发模式: 明文密码比对 (跳过 BCrypt). 通过 JVM 系统属性启用:
     *   -Daiplatform.auth.dev-plain-password=true
     * 或环境变量 AI_AUTH_DEV_PLAIN_PASSWORD=true
     *
     * <p>⚠️ 仅用于开发/沙箱调试, 生产严禁开启. 开启后密码明文存储/比对,
     * 安全审计能看到 FAILED/明文模式 提示.</p>
     */
    private boolean isDevModePlainPassword() {
        // 1) JVM 系统属性
        if ("true".equalsIgnoreCase(System.getProperty("aiplatform.auth.dev-plain-password"))) return true;
        // 2) 环境变量
        if ("true".equalsIgnoreCase(System.getenv("AI_AUTH_DEV_PLAIN_PASSWORD"))) return true;
        return false;
    }

    private boolean isDevModePlainPassword(boolean headerFlag) {
        // headerFlag = 前端 X-Dev-Plain-Password 请求头
        // 安全: 即使前端传了头, 后端仍要求环境变量/系统属性开启才生效
        return headerFlag && isDevModePlainPassword();
    }

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
        return login(req, null, null, false);
    }

    public LoginResponse login(LoginRequest req, String ip, String userAgent) {
        return login(req, ip, userAgent, false);
    }

    public LoginResponse login(LoginRequest req, String ip, String userAgent, boolean devPlainHeader) {
        Result<Map<String, Object>> resp = userServiceClient.getByUsername(req.getUsername());
        if (resp == null || resp.getCode() == null || resp.getCode() != ResultCode.SUCCESS.getCode().intValue()) {
            recordAudit(req.getUsername(), null, null, ip, userAgent, "FAILED", "用户不存在");
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        Map<String, Object> user = resp.getData();
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        Integer status = (Integer) user.get("status");
        if (status != null && status == 0) {
            recordAudit(req.getUsername(), ((Number) user.get("id")).longValue(), null, ip, userAgent, "LOCKED", "账号已停用");
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        String stored = (String) user.get("password");
        if (stored == null) {
            recordAudit(req.getUsername(), ((Number) user.get("id")).longValue(), null, ip, userAgent, "FAILED", "存储密码为空");
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        // 密码验证: 默认 BCrypt, dev 模式 (前端头 + 后端 env) 走明文比对
        boolean useDevPlain = devPlainHeader && isDevModePlainPassword();
        boolean passwordOk = useDevPlain
                ? req.getPassword().equals(stored)
                : passwordEncoder.matches(req.getPassword(), stored);
        if (!passwordOk) {
            recordAudit(req.getUsername(), ((Number) user.get("id")).longValue(), null, ip, userAgent, "FAILED",
                    useDevPlain ? "密码错误 (明文模式)" : "密码错误");
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

        recordAudit(username, userId, tenantId, ip, userAgent, "SUCCESS", null);

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

    private void recordAudit(String username, Long userId, Long tenantId, String ip, String userAgent, String status, String reason) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("username", username);
            body.put("userId", userId);
            body.put("tenantId", tenantId);
            body.put("ip", ip);
            body.put("userAgent", userAgent);
            body.put("status", status);
            body.put("reason", reason);
            systemAuditClient.record(body);
        } catch (Exception e) {
            log.warn("记录登录审计失败: {}", e.getMessage());
        }
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

    /**
     * 注册新用户.
     * <ol>
     *   <li>校验手机号未被注册 (通过 user-service feign)</li>
     *   <li>校验公司 (tenant) 存在且 status=1</li>
     *   <li>校验验证码 (演示版固定 123456)</li>
     *   <li>BCrypt 加密密码</li>
     *   <li>调用 user-service 创建用户 (feign 写 sys_user + sys_user_tenant)</li>
     * </ol>
     */
    public com.aiplatform.auth.dto.RegisterResponse register(
            com.aiplatform.auth.dto.RegisterRequest req, String ip, String userAgent, boolean devPlainHeader) {

        // 1) 校验验证码 (演示版)
        if (!"123456".equals(req.getCaptcha())) {
            recordAudit(req.getPhone(), null, req.getTenantId(), ip, userAgent, "FAILED", "验证码错误");
            throw new BusinessException(ResultCode.BAD_REQUEST, "验证码错误");
        }

        // 2) 校验公司 (调用 user-service feign)
        com.aiplatform.common.result.Result<java.util.Map<String, Object>> tenantResp;
        try {
            tenantResp = tenantServiceClient.byId(req.getTenantId());
        } catch (Exception e) {
            log.error("调用 user-service 查 tenant 失败: {}", e.getMessage());
            recordAudit(req.getPhone(), null, req.getTenantId(), ip, userAgent, "FAILED", "租户服务不可用");
            throw new BusinessException(ResultCode.FAIL, "租户服务不可用: " + e.getMessage());
        }
        if (tenantResp == null || tenantResp.getData() == null) {
            recordAudit(req.getPhone(), null, req.getTenantId(), ip, userAgent, "FAILED", "公司不存在");
            throw new BusinessException(ResultCode.BAD_REQUEST, "公司不存在");
        }
        Object status = tenantResp.getData().get("status");
        if (status != null && "0".equals(String.valueOf(status))) {
            recordAudit(req.getPhone(), null, req.getTenantId(), ip, userAgent, "FAILED", "公司已停用");
            throw new BusinessException(ResultCode.BAD_REQUEST, "该公司已停用, 不可注册");
        }

        // 3) 校验手机号唯一 (user-service feign)
        com.aiplatform.common.result.Result<java.util.Map<String, Object>> exists;
        try {
            exists = userServiceClient.getByUsername(req.getPhone());
        } catch (Exception e) {
            log.error("调用 user-service 查 byUsername 失败: {}", e.getMessage());
            recordAudit(req.getPhone(), null, req.getTenantId(), ip, userAgent, "FAILED", "用户服务不可用");
            throw new BusinessException(ResultCode.FAIL, "用户服务不可用: " + e.getMessage());
        }
        if (exists != null && exists.getData() != null) {
            recordAudit(req.getPhone(), null, req.getTenantId(), ip, userAgent, "FAILED", "手机号已注册");
            throw new BusinessException(ResultCode.BAD_REQUEST, "该手机号已注册");
        }

        // 4) 加密密码 (dev 模式明文)
        String stored = useDevPlain(devPlainHeader)
                ? req.getPassword()
                : passwordEncoder.encode(req.getPassword());

        // 5) 调 user-service 创建用户
        java.util.Map<String, Object> createBody = new java.util.HashMap<>();
        createBody.put("username", req.getPhone());
        createBody.put("password", stored);
        createBody.put("nickname", "新用户" + req.getPhone().substring(7));
        createBody.put("phone", req.getPhone());
        createBody.put("email", null);
        createBody.put("department", req.getDepartment());
        createBody.put("tenantId", req.getTenantId());
        createBody.put("status", 1);

        com.aiplatform.common.result.Result<java.util.Map<String, Object>> createResp;
        try {
            createResp = userServiceClient.create(createBody);
        } catch (Exception e) {
            log.error("调用 user-service 创建用户失败: {}", e.getMessage());
            recordAudit(req.getPhone(), null, req.getTenantId(), ip, userAgent, "FAILED", "创建用户失败");
            throw new BusinessException(ResultCode.FAIL, "创建用户失败: " + e.getMessage());
        }
        if (createResp == null || createResp.getCode() == null || createResp.getCode() != 0) {
            String msg = createResp != null && createResp.getMessage() != null
                    ? createResp.getMessage() : "未知错误";
            recordAudit(req.getPhone(), null, req.getTenantId(), ip, userAgent, "FAILED", "创建用户失败: " + msg);
            throw new BusinessException(ResultCode.FAIL, "创建用户失败: " + msg);
        }

        Object newUserId = createResp.getData() != null ? createResp.getData().get("id") : null;
        recordAudit(req.getPhone(),
                newUserId instanceof Number ? ((Number) newUserId).longValue() : null,
                req.getTenantId(), ip, userAgent, "REGISTER_OK", "注册成功");

        com.aiplatform.auth.dto.RegisterResponse resp = new com.aiplatform.auth.dto.RegisterResponse();
        resp.setUserId(newUserId instanceof Number ? ((Number) newUserId).longValue() : null);
        resp.setUsername(req.getPhone());
        resp.setTenantId(req.getTenantId());
        resp.setMessage("注册成功, 请登录");
        return resp;
    }

    private boolean useDevPlain(boolean headerFlag) {
        return headerFlag && isDevModePlainPassword();
    }
}
