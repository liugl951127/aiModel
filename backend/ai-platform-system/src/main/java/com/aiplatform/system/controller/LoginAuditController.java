package com.aiplatform.system.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.system.entity.SysLoginAudit;
import com.aiplatform.system.service.LoginAuditService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit/login")
@RequiredArgsConstructor
public class LoginAuditController {
    private final LoginAuditService auditService;

    @GetMapping("/page")
    public Result<IPage<SysLoginAudit>> page(@RequestParam(defaultValue = "1") int current,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String username,
                                             @RequestParam(required = false) String status) {
        return Result.success(auditService.page(current, size, username, status));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(auditService.stats());
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.success(auditService.trend(days));
    }

    /**
     * 内部 Feign 端点：auth-service 记录登录审计 (不走 gateway).
     * body: { username, tenantId, userId, ip, userAgent, status, reason }
     */
    @PostMapping("/feign/record")
    public Result<Void> record(@RequestBody java.util.Map<String, Object> body) {
        auditService.record(
                (String) body.get("username"),
                body.get("tenantId") == null ? null : ((Number) body.get("tenantId")).longValue(),
                body.get("userId") == null ? null : ((Number) body.get("userId")).longValue(),
                (String) body.get("ip"),
                (String) body.get("userAgent"),
                (String) body.get("status"),
                (String) body.get("reason")
        );
        return Result.success();
    }
}
