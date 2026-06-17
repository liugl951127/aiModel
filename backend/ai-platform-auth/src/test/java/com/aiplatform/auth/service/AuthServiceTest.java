package com.aiplatform.auth.service;

import com.aiplatform.auth.dto.LoginRequest;
import com.aiplatform.auth.dto.LoginResponse;
import com.aiplatform.auth.feign.SystemAuditClient;
import com.aiplatform.auth.feign.TenantServiceClient;
import com.aiplatform.auth.feign.UserServiceClient;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.Result;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.common.util.JwtUtils;
import com.aiplatform.starter.redis.RedisUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthService 登录逻辑单元测试.
 *
 * <p>覆盖主要分支:</p>
 * <ul>
 *   <li>超级管理员 admin 登录 (自动拥有所有公司, tenantId 0L)</li>
 *   <li>普通用户登录 (tenantId 由 user.tenantId 决定)</li>
 *   <li>用户不存在 (404)</li>
 *   <li>账号已停用 (LOCKED)</li>
 *   <li>密码错误 (BCrypt 校验)</li>
 *   <li>用户不属于所选公司 (FORBIDDEN)</li>
 *   <li>审计记录被调用</li>
 *   <li>refreshToken 写入 Redis</li>
 *   <li>preview 接口 (前端先查公司再登录)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 登录逻辑测试")
class AuthServiceTest {

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private RedisUtils redisUtils;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private TenantServiceClient tenantServiceClient;
    @Mock
    private SystemAuditClient systemAuditClient;

    @InjectMocks
    private AuthService authService;

    // BCrypt hash for "admin123" (project's own encoder for compat)
    private static final String BCRYPT_ADMIN_123 =
            "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";

    @BeforeEach
    void setUp() {
        // mock jwt 行为: generate 返回固定 token, getExpiration 返回 7200s
        // lenient: 不是每个测试都需要 (例如用户不存在的场景)
        lenient().when(jwtUtils.generate(anyLong(), anyString(), any(), any()))
                .thenReturn("access-token-xxx");
        lenient().when(jwtUtils.getExpiration()).thenReturn(7_200_000L);
    }

    // ============ 1. admin 超级管理员登录 ============

    @Test
    @DisplayName("admin 登录: 无 tenantId → 自动 SUPER_TENANT_ID=0L, roles=[SUPER_ADMIN,PLATFORM_ADMIN,user]")
    void login_superAdmin_noTenantId_succeeds() {
        // given: admin 用户, status=1
        Map<String, Object> user = Map.of(
                "id", 1L,
                "username", "admin",
                "password", BCRYPT_ADMIN_123,
                "nickname", "超级管理员",
                "department", "总部"
        );
        Result<Map<String, Object>> userResp = Result.success(user);
        when(userServiceClient.getByUsername("admin")).thenReturn(userResp);
        // super admin 不查 tenant, 不需要 stub listByUsername

        // when
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin123");
        LoginResponse resp = authService.login(req, "127.0.0.1", "Mozilla");

        // then
        assertNotNull(resp);
        assertEquals("access-token-xxx", resp.getAccessToken());
        assertNotNull(resp.getRefreshToken());
        assertEquals(1L, resp.getUserId());
        assertEquals("admin", resp.getUsername());
        // ★ 关键: admin 自动拥有所有公司
        assertEquals(1L, resp.getTenantId());
        assertEquals("ALL", resp.getTenantCode());
        assertEquals("全部公司（超级管理员）", resp.getTenantName());
        assertEquals(List.of("SUPER_ADMIN", "PLATFORM_ADMIN", "user"), resp.getRoles());
        assertEquals("Bearer", resp.getTokenType());

        // redis 写入 refresh token
        verify(redisUtils).set(eq("refresh:" + resp.getRefreshToken()), eq("access-token-xxx"), eq(7L * 24 * 3600));

        // 审计: SUCCESS
        ArgumentCaptor<Map<String, Object>> auditCap = ArgumentCaptor.forClass(Map.class);
        verify(systemAuditClient).record(auditCap.capture());
        Map<String, Object> audit = auditCap.getValue();
        assertEquals("admin", audit.get("username"));
        assertEquals("SUCCESS", audit.get("status"));
    }

    // ============ 2. 普通用户登录 ============

    @Test
    @DisplayName("普通用户登录: 没传 tenantId → 用 user.tenantId=100, roles=[user]")
    void login_normalUser_usesUserTenantId() {
        // given: 用户 zhangsan, 属于公司 100
        Map<String, Object> user = Map.of(
                "id", 42L,
                "username", "zhangsan",
                "password", BCRYPT_ADMIN_123,
                "tenantId", 100L,
                "nickname", "张三"
        );
        when(userServiceClient.getByUsername("zhangsan"))
                .thenReturn(Result.success(user));

        // when
        LoginRequest req = new LoginRequest();
        req.setUsername("zhangsan");
        req.setPassword("admin123");
        LoginResponse resp = authService.login(req);

        // then
        assertNotNull(resp);
        assertEquals(42L, resp.getUserId());
        assertEquals(100L, resp.getTenantId());
        assertEquals(List.of("user"), resp.getRoles());
        assertNull(resp.getTenantCode());  // 普通用户没查 tenant 详情
    }

    @Test
    @DisplayName("普通用户登录: 传了 tenantId → 校验用户属于该公司")
    void login_normalUser_validatesTenantMembership() {
        Map<String, Object> user = Map.of(
                "id", 42L,
                "username", "zhangsan",
                "password", BCRYPT_ADMIN_123,
                "tenantId", 100L
        );
        when(userServiceClient.getByUsername("zhangsan"))
                .thenReturn(Result.success(user));
        // 模拟 listByUsername: 用户只属于 [100, 200], 没传 999
        Result<List<Map<String, Object>>> tenantsResp = Result.success(List.of(
                Map.of("id", 100L, "tenantCode", "A", "tenantName", "甲公司"),
                Map.of("id", 200L, "tenantCode", "B", "tenantName", "乙公司")
        ));
        when(tenantServiceClient.listByUsername("zhangsan")).thenReturn(tenantsResp);

        // when + then: 传 tenantId=999 (不属于) → FORBIDDEN
        LoginRequest req = new LoginRequest();
        req.setUsername("zhangsan");
        req.setPassword("admin123");
        req.setTenantId(999L);
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode().intValue());
        assertTrue(ex.getMessage().contains("该用户不属于此公司"));
    }

    @Test
    @DisplayName("普通用户登录: 传了 tenantId, 属于该公司 → 拿到 tenantCode/Name")
    void login_normalUser_withValidTenantId() {
        Map<String, Object> user = Map.of(
                "id", 42L,
                "username", "zhangsan",
                "password", BCRYPT_ADMIN_123,
                "tenantId", 100L
        );
        when(userServiceClient.getByUsername("zhangsan"))
                .thenReturn(Result.success(user));
        Result<List<Map<String, Object>>> tenantsResp = Result.success(List.of(
                Map.of("id", 100L, "tenantCode", "A", "tenantName", "甲公司"),
                Map.of("id", 200L, "tenantCode", "B", "tenantName", "乙公司")
        ));
        when(tenantServiceClient.listByUsername("zhangsan")).thenReturn(tenantsResp);

        LoginRequest req = new LoginRequest();
        req.setUsername("zhangsan");
        req.setPassword("admin123");
        req.setTenantId(200L);
        LoginResponse resp = authService.login(req);

        assertEquals(200L, resp.getTenantId());
        assertEquals("B", resp.getTenantCode());
        assertEquals("乙公司", resp.getTenantName());
    }

    // ============ 3. 错误分支 ============

    @Test
    @DisplayName("用户不存在 → USER_PASSWORD_ERROR + 审计 FAILED")
    void login_userNotFound_throwsAndAudits() {
        // userServiceClient.getByUsername 返回 null (服务不可达)
        when(userServiceClient.getByUsername("nobody")).thenReturn(null);

        LoginRequest req = new LoginRequest();
        req.setUsername("nobody");
        req.setPassword("any");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(ResultCode.USER_PASSWORD_ERROR.getCode(), ex.getCode().intValue());

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(systemAuditClient).record(cap.capture());
        assertEquals("FAILED", cap.getValue().get("status"));
        assertEquals("用户不存在", cap.getValue().get("reason"));
    }

    @Test
    @DisplayName("账号已停用 (status=0) → USER_DISABLED + 审计 LOCKED")
    void login_disabledAccount_throwsAndAudits() {
        Map<String, Object> user = Map.of(
                "id", 7L,
                "username", "inactive",
                "password", BCRYPT_ADMIN_123,
                "status", 0
        );
        when(userServiceClient.getByUsername("inactive"))
                .thenReturn(Result.success(user));

        LoginRequest req = new LoginRequest();
        req.setUsername("inactive");
        req.setPassword("admin123");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(ResultCode.USER_DISABLED.getCode(), ex.getCode().intValue());

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(systemAuditClient).record(cap.capture());
        assertEquals("LOCKED", cap.getValue().get("status"));
    }

    @Test
    @DisplayName("密码错误 → USER_PASSWORD_ERROR + 审计 FAILED, 不写 Redis")
    void login_wrongPassword_throwsAndAudits() {
        Map<String, Object> user = Map.of(
                "id", 8L,
                "username", "alice",
                "password", BCRYPT_ADMIN_123
        );
        when(userServiceClient.getByUsername("alice"))
                .thenReturn(Result.success(user));

        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("WRONG");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(ResultCode.USER_PASSWORD_ERROR.getCode(), ex.getCode().intValue());

        // ★ 关键: 失败不能写 redis
        verify(redisUtils, never()).set(anyString(), anyString(), anyLong());

        // 审计
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(systemAuditClient).record(cap.capture());
        assertEquals("FAILED", cap.getValue().get("status"));
        assertEquals("密码错误", cap.getValue().get("reason"));
    }

    @Test
    @DisplayName("user.data=null (用户被删了) → USER_NOT_FOUND")
    void login_userDataNull_throws() {
        // resp.code=0 但 resp.data=null (异常数据)
        when(userServiceClient.getByUsername("ghost"))
                .thenReturn(Result.success(null));

        LoginRequest req = new LoginRequest();
        req.setUsername("ghost");
        req.setPassword("any");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(ResultCode.USER_NOT_FOUND.getCode(), ex.getCode().intValue());
    }

    @Test
    @DisplayName("存储密码为 null → USER_PASSWORD_ERROR + 审计 \"存储密码为空\"")
    void login_nullPassword_throws() {
        Map<String, Object> user = new java.util.HashMap<>();
        user.put("id", 9L);
        user.put("username", "weird");
        user.put("password", null);  // 异常: 密码字段是 null
        when(userServiceClient.getByUsername("weird"))
                .thenReturn(Result.success(user));

        LoginRequest req = new LoginRequest();
        req.setUsername("weird");
        req.setPassword("any");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(ResultCode.USER_PASSWORD_ERROR.getCode(), ex.getCode().intValue());

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(systemAuditClient).record(cap.capture());
        assertEquals("存储密码为空", cap.getValue().get("reason"));
    }

    // ============ 4. preview 接口 ============

    @Test
    @DisplayName("preview: admin 拿到 [ALL] 虚拟公司, roles=[SUPER_ADMIN,PLATFORM_ADMIN,user]")
    void preview_admin_returnsAllTenant() {
        Map<String, Object> user = Map.of(
                "id", 1L,
                "username", "admin",
                "nickname", "超管",
                "department", "总部"
        );
        when(userServiceClient.getByUsername("admin")).thenReturn(Result.success(user));
        // admin 走虚拟公司分支, 不调 listByUsername — stub 防止 NPE (应该不被调用)
        lenient().when(tenantServiceClient.listByUsername("admin")).thenReturn(null);

        Map<String, Object> safe = authService.preview("admin");

        assertEquals(1L, safe.get("userId"));
        assertEquals(true, safe.get("isSuperAdmin"));
        assertEquals(List.of("SUPER_ADMIN", "PLATFORM_ADMIN", "user"), safe.get("roles"));
        List<?> tenants = (List<?>) safe.get("tenants");
        // ★ admin 拿到虚拟 [ALL] 公司, 1 个
        assertEquals(1, tenants.size(), "admin preview 应返回 1 个虚拟公司");
        Map<?, ?> t = (Map<?, ?>) tenants.get(0);
        assertEquals(0L, t.get("id"));
        assertEquals("ALL", t.get("tenantCode"));
    }

    @Test
    @DisplayName("preview: 普通用户拿到自己的公司列表, roles=[user]")
    void preview_normalUser_returnsOwnTenants() {
        Map<String, Object> user = Map.of(
                "id", 42L,
                "username", "zhangsan",
                "nickname", "张三"
        );
        when(userServiceClient.getByUsername("zhangsan")).thenReturn(Result.success(user));
        Result<List<Map<String, Object>>> tenantsResp = Result.success(List.of(
                Map.of("id", 100L, "tenantCode", "A", "tenantName", "甲公司"),
                Map.of("id", 200L, "tenantCode", "B", "tenantName", "乙公司")
        ));
        when(tenantServiceClient.listByUsername("zhangsan")).thenReturn(tenantsResp);

        Map<String, Object> safe = authService.preview("zhangsan");

        assertEquals(42L, safe.get("userId"));
        assertEquals(false, safe.get("isSuperAdmin"));
        assertEquals(List.of("user"), safe.get("roles"));
        assertEquals(2, ((List<?>) safe.get("tenants")).size());
    }

    @Test
    @DisplayName("preview: 用户不存在 → USER_NOT_FOUND")
    void preview_userNotFound_throws() {
        when(userServiceClient.getByUsername("nobody")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.preview("nobody"));
        assertEquals(ResultCode.USER_NOT_FOUND.getCode(), ex.getCode().intValue());
    }

    @Test
    @DisplayName("preview: 空 username → BAD_REQUEST")
    void preview_emptyUsername_throws() {
        assertThrows(BusinessException.class, () -> authService.preview(""));
        assertThrows(BusinessException.class, () -> authService.preview(null));
        verifyNoInteractions(userServiceClient);
    }

    // ============ 5. refresh / logout ============

    @Test
    @DisplayName("refresh: Redis 拿到 access token → 返回")
    void refresh_redisHit_returnsAccess() {
        when(redisUtils.get("refresh:abc")).thenReturn("access-token-xyz");

        String access = authService.refresh("abc");

        assertEquals("access-token-xyz", access);
    }

    @Test
    @DisplayName("refresh: Redis miss (token 失效) → TOKEN_EXPIRED")
    void refresh_redisMiss_throws() {
        when(redisUtils.get("refresh:expired")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.refresh("expired"));
        assertEquals(ResultCode.TOKEN_EXPIRED.getCode(), ex.getCode().intValue());
    }

    @Test
    @DisplayName("logout: null token → 静默不报错")
    void logout_nullToken_silent() {
        assertDoesNotThrow(() -> authService.logout(null));
        verify(redisUtils, never()).delete(anyString());
    }

    @Test
    @DisplayName("logout: 有效 token → 删 Redis")
    void logout_validToken_deletes() {
        authService.logout("token-123");
        verify(redisUtils).delete("token:token-123");
    }
}
