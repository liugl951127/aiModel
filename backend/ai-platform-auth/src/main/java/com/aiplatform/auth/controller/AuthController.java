package com.aiplatform.auth.controller;

import com.aiplatform.auth.dto.LoginRequest;
import com.aiplatform.auth.dto.LoginResponse;
import com.aiplatform.auth.service.AuthService;
import com.aiplatform.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        String token = auth == null ? null : (auth.startsWith("Bearer ") ? auth.substring(7) : auth);
        authService.logout(token);
        return Result.success();
    }

    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(@RequestParam String refreshToken) {
        return Result.success(Map.of("accessToken", authService.refresh(refreshToken)));
    }

    @GetMapping("/captcha")
    public Result<Map<String, Object>> captcha() {
        // 简化版：返回固定图形 captcha
        String id = java.util.UUID.randomUUID().toString().replace("-", "");
        return Result.success(Map.of("captchaId", id, "code", "abcd"));
    }
}
