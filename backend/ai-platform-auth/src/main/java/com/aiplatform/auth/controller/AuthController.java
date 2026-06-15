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

    /**
     * 登录前置查询：根据用户名拿用户信息 + 该用户可登录的公司列表。
     *
     * <p>前端流程：用户输完用户名 → 调这个接口 → 弹公司下拉 → 用户选完公司
     * 输密码 → 调 /login。体验类似钉钉/飞书企业登录。</p>
     */
    @GetMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestParam String username) {
        return Result.success(authService.preview(username));
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
        String id = java.util.UUID.randomUUID().toString().replace("-", "");
        return Result.success(Map.of("captchaId", id, "code", "abcd"));
    }
}
