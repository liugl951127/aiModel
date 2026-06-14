package com.aiplatform.auth.service;

import com.aiplatform.auth.dto.LoginRequest;
import com.aiplatform.auth.dto.LoginResponse;
import com.aiplatform.auth.feign.UserServiceClient;
import com.aiplatform.common.constant.CommonConstants;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.Result;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.common.util.JwtUtils;
import com.aiplatform.common.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;
    private final UserServiceClient userServiceClient;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
        Long tenantId = req.getTenantId() != null
                ? req.getTenantId()
                : user.get("tenantId") == null ? CommonConstants.SUPER_TENANT_ID
                    : ((Number) user.get("tenantId")).longValue();

        String accessToken = jwtUtils.generate(userId, username, tenantId);
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        redisUtils.set("refresh:" + refreshToken, accessToken, 7 * 24 * 3600);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtils.getExpiration() / 1000)
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
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
