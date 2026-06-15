package com.aiplatform.seata.user.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.seata.user.entity.UserCredits;
import com.aiplatform.seata.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User service HTTP 端点（模拟独立微服务）。
 * 真实部署时这会是 :9002 / 单独 jar；本 demo 用路径前缀 /api/seata/user 区分。
 */
@RestController
@RequestMapping("/api/seata/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public Result<UserCredits> get(@PathVariable Long userId) {
        return Result.success(userService.get(userId));
    }

    @PostMapping("/deduct")
    public Result<Long> deduct(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        Long tokens = ((Number) body.get("tokens")).longValue();
        return Result.success(userService.deduct(userId, tokens));
    }
}
