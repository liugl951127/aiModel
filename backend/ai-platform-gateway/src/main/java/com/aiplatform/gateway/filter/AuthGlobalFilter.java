package com.aiplatform.gateway.filter;

import com.aiplatform.common.constant.CommonConstants;
import com.aiplatform.common.util.JwtUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Gateway-side JWT verification. Strips the token down to a verified claims map and
 * injects the resolved tenantId / userId into downstream headers so services can stay
 * auth-light.
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITELIST_PREFIXES = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/captcha",
            "/api/auth/refresh",
            "/api/public/",
            "/actuator/",
            "/v3/api-docs",
            "/doc.html",
            "/swagger-ui",
            "/favicon.ico"
    );

    @Autowired
    private JwtUtils jwtUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String auth = request.getHeaders().getFirst(CommonConstants.HEADER_AUTH);
        if (auth == null || !auth.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return unauthorized(exchange, "未登录");
        }
        String token = auth.substring(CommonConstants.TOKEN_PREFIX.length());
        if (!jwtUtils.validate(token)) {
            return unauthorized(exchange, "Token 无效或已过期");
        }

        try {
            var claims = jwtUtils.parse(token);
            ServerHttpRequest mutated = request.mutate()
                    .header(CommonConstants.HEADER_TENANT_ID, String.valueOf(claims.get(CommonConstants.CLAIM_TENANT_ID)))
                    .header("X-User-Id", String.valueOf(claims.get(CommonConstants.CLAIM_USER_ID)))
                    .header("X-Username", String.valueOf(claims.get(CommonConstants.CLAIM_USERNAME)))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            log.error("[GATEWAY] parse token error: {}", e.getMessage());
            return unauthorized(exchange, "Token 解析失败");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            // Map.of 不接受 null value，统一用 HashMap
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("code", 401);
            payload.put("message", msg);
            payload.put("data", null);
            byte[] body = objectMapper.writeValueAsBytes(payload);
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            byte[] body = ("{\"code\":401,\"message\":\"" + msg + "\"}").getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        }
    }
}
