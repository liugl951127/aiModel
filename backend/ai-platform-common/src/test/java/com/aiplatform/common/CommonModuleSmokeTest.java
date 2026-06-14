package com.aiplatform.common;

import com.aiplatform.common.result.Result;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.tenant.TenantContext;
import com.aiplatform.common.util.JwtUtils;
import com.aiplatform.common.util.SnowflakeIdGenerator;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test for the common module: pure utilities only, no Spring context.
 */
class CommonModuleSmokeTest {

    @Test
    void resultSuccessAndFail() {
        Result<String> ok = Result.success("hello");
        assertEquals(200, ok.getCode());
        assertEquals("hello", ok.getData());

        Result<Void> bad = Result.fail(ResultCode.USER_NOT_FOUND);
        assertEquals(2001, bad.getCode());
    }

    @Test
    void businessExceptionCarriesCode() {
        BusinessException e = new BusinessException(ResultCode.AGENT_EXECUTE_ERROR, "boom");
        assertEquals(4001, e.getCode());
        assertEquals("boom", e.getMessage());
    }

    @Test
    void tenantContextLifecycle() {
        assertNull(TenantContext.getTenantId());
        TenantContext.setTenantId(42L);
        TenantContext.setTenantCode("acme");
        assertEquals(42L, TenantContext.getTenantId());
        assertEquals("acme", TenantContext.getTenantCode());
        TenantContext.clear();
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getTenantCode());
    }

    @Test
    void jwtRoundTrip() {
        JwtUtils jwt = new JwtUtils("test-secret-key-32bytes-min-padding",
                3600_000L, "test-issuer");
        String token = jwt.generate(99L, "alice", 7L);
        assertTrue(jwt.validate(token));
        Claims c = jwt.parse(token);
        assertEquals(99L, ((Number) c.get("userId")).longValue());
        assertEquals(7L, ((Number) c.get("tenantId")).longValue());
    }

    @Test
    void jwtRejectsShortSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtUtils("short", 1000L, "x"));
    }

    @Test
    void snowflakeGeneratesUnique() {
        SnowflakeIdGenerator g = new SnowflakeIdGenerator();
        long a = g.nextId();
        long b = g.nextId();
        assertTrue(a > 0);
        assertTrue(b > 0);
        assertNotEquals(a, b);
    }
}
