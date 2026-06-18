package com.aiplatform.system.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.system.entity.SysOperationAudit;
import com.aiplatform.system.service.OperationAuditService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ★ P0-LEAD-1 操作审计查询端点.
 */
@RestController
@RequestMapping("/api/audit/operation")
@RequiredArgsConstructor
public class OperationAuditController {
    private final OperationAuditService auditService;

    @GetMapping("/page")
    public Result<IPage<SysOperationAudit>> page(@RequestParam(defaultValue = "1") int current,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String username,
                                                  @RequestParam(required = false) String module,
                                                  @RequestParam(required = false) String operation) {
        return Result.success(auditService.page(current, size, username, module, operation));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(auditService.stats());
    }
}
