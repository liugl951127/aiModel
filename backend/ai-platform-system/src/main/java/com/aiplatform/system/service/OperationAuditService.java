package com.aiplatform.system.service;

import com.aiplatform.system.entity.SysOperationAudit;
import com.aiplatform.system.mapper.SysOperationAuditMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ★ P0-LEAD-1 操作审计服务 (处长要求: 可追溯).
 * 自动记录所有 Create/Update/Delete 操作, 异步落库不阻塞主请求.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationAuditService {

    private final SysOperationAuditMapper auditMapper;

    /**
     * 异步落库 (用 @Async 免阻塞主业务, 失败不影响业务).
     */
    @Async
    public void record(String username, Long userId, Long tenantId,
                       String module, String operation, String description,
                       String bizId, String httpMethod, String requestPath,
                       String requestParams, Integer responseCode,
                       String clientIp, String userAgent, Long costMs,
                       Throwable error) {
        try {
            SysOperationAudit a = new SysOperationAudit();
            a.setUsername(safeStr(username, "anonymous"));
            a.setUserId(userId);
            a.setTenantId(tenantId);
            a.setModule(module);
            a.setOperation(operation);
            a.setDescription(description);
            a.setBizId(bizId);
            a.setHttpMethod(httpMethod);
            a.setRequestPath(requestPath);
            a.setRequestParams(mask(requestParams));
            a.setResponseCode(responseCode);
            a.setClientIp(clientIp);
            a.setUserAgent(safeStr(userAgent, 200));
            a.setCostMs(costMs);
            a.setStatus(error == null ? "SUCCESS" : "FAILED");
            a.setErrorMessage(error != null ? safeStr(error.getMessage(), 500) : null);
            a.setCreateTime(LocalDateTime.now());
            auditMapper.insert(a);
        } catch (Exception e) {
            log.error("[Audit] 写审计日志失败, 不影响业务: {}", e.getMessage());
        }
    }

    /**
     * 简版便捷重载.
     */
    public void recordSimple(String username, Long userId, Long tenantId,
                             String module, String operation, String description,
                             String bizId) {
        record(username, userId, tenantId, module, operation, description, bizId,
                null, null, null, 200, null, null, null, null);
    }

    /**
     * 分页查询.
     */
    public IPage<SysOperationAudit> page(int current, int size, String username, String module, String operation) {
        LambdaQueryWrapper<SysOperationAudit> q = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) q.eq(SysOperationAudit::getUsername, username);
        if (module != null && !module.isBlank()) q.eq(SysOperationAudit::getModule, module);
        if (operation != null && !operation.isBlank()) q.eq(SysOperationAudit::getOperation, operation);
        q.orderByDesc(SysOperationAudit::getCreateTime);
        return auditMapper.selectPage(new Page<>(current, size), q);
    }

    /**
     * 统计: 今日操作 / 失败 / 各模块占比.
     */
    public Map<String, Object> stats() {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        Long total = auditMapper.selectCount(new LambdaQueryWrapper<SysOperationAudit>().ge(SysOperationAudit::getCreateTime, today));
        Long failed = auditMapper.selectCount(new LambdaQueryWrapper<SysOperationAudit>()
                .ge(SysOperationAudit::getCreateTime, today).eq(SysOperationAudit::getStatus, "FAILED"));
        Long success = total - failed;
        Map<String, Object> r = new HashMap<>();
        r.put("total", total);
        r.put("success", success);
        r.put("failed", failed);
        r.put("date", today.toLocalDate().toString());
        return r;
    }

    /** 简单脱敏: 密码字段替换 */
    private String mask(String s) {
        if (s == null) return null;
        return s.replaceAll("(?i)(\"password\"\\s*[:=]\\s*\")[^\"]*", "$1***")
                .replaceAll("(?i)(password=)[^&\\s]+", "$1***");
    }

    private String safeStr(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
