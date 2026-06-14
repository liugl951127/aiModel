package com.aiplatform.common.util;

import com.aiplatform.common.constant.CommonConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT utility - sign / parse / validate tokens.
 *
 * Configurable via {@code aiplatform.jwt.*} properties.
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aiplatform.jwt")
public class JwtUtils {

    /** HMAC-SHA secret, must be >= 32 bytes for HS256. */
    private String secret = "ai-platform-default-secret-key-please-change-32+";

    /** Expiration in milliseconds. Default 24h. */
    private long expiration = 24L * 60L * 60L * 1000L;

    /** Token issuer. */
    private String issuer = "ai-platform";

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String username, Long tenantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CommonConstants.CLAIM_USER_ID, userId);
        claims.put(CommonConstants.CLAIM_USERNAME, username);
        claims.put(CommonConstants.CLAIM_TENANT_ID, tenantId);
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
            log.debug("[JWT] invalid token: {}", e.getMessage());
            return false;
        }
    }
}
