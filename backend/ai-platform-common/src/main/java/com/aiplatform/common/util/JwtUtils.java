package com.aiplatform.common.util;

import com.aiplatform.common.constant.CommonConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure Java JWT utility. No Spring annotations so it can be used from
 * non-Spring modules too. The Spring-aware binding lives in the
 * secure-starter which constructs a singleton instance and binds config.
 */
public class JwtUtils {

    private final String secret;
    private final long expiration;
    private final String issuer;
    private final SecretKey key;

    public JwtUtils(String secret, long expiration, String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be >= 32 bytes for HS256");
        }
        this.secret = secret;
        this.expiration = expiration;
        this.issuer = issuer;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String username, Long tenantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CommonConstants.CLAIM_USER_ID, userId);
        claims.put(CommonConstants.CLAIM_USERNAME, username);
        claims.put(CommonConstants.CLAIM_TENANT_ID, tenantId);
        return generate(claims);
    }

    /**
     * 生成带部门标识的 JWT。
     *
     * <p>登录时用户选完公司 + 部门（"部职"）后调用。{@code department} 字段
     * 放部门字符串（如"研发部"），后续日志 / 智能体上下文可读取显示。</p>
     */
    public String generate(Long userId, String username, Long tenantId, String department) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CommonConstants.CLAIM_USER_ID, userId);
        claims.put(CommonConstants.CLAIM_USERNAME, username);
        claims.put(CommonConstants.CLAIM_TENANT_ID, tenantId);
        if (department != null) claims.put("department", department);
        return generate(claims);
    }

    public String generate(Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .issuer(issuer)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiration))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Object getClaim(String token, String claimName) {
        return parse(token).get(claimName);
    }

    public boolean isExpired(String token) {
        try {
            return parse(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpiration() {
        return expiration;
    }

    public String getSecret() {
        return secret;
    }
}
