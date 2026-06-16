package com.aiplatform.system.service;

import com.aiplatform.system.entity.SysLoginAudit;
import com.aiplatform.system.mapper.SysLoginAuditMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoginAuditService {
    private final SysLoginAuditMapper auditMapper;

    public void record(String username, Long tenantId, Long userId, String ip,
                       String userAgent, String status, String reason) {
        SysLoginAudit a = new SysLoginAudit();
        a.setUsername(username);
        a.setTenantId(tenantId);
        a.setUserId(userId);
        a.setLoginIp(ip);
        a.setUserAgent(userAgent);
        a.setLoginStatus(status);
        a.setFailReason(reason);
        a.setLoginTime(LocalDateTime.now());
        auditMapper.insert(a);
    }

    public IPage<SysLoginAudit> page(int current, int size, String username, String status) {
        LambdaQueryWrapper<SysLoginAudit> q = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) q.eq(SysLoginAudit::getUsername, username);
        if (status != null && !status.isBlank()) q.eq(SysLoginAudit::getLoginStatus, status);
        q.orderByDesc(SysLoginAudit::getLoginTime);
        return auditMapper.selectPage(new Page<>(current, size), q);
    }

    /** 简单统计: 今日成功 / 失败 / 锁定 / 总数 */
    public Map<String, Object> stats() {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        Long success = auditMapper.selectCount(new LambdaQueryWrapper<SysLoginAudit>()
                .eq(SysLoginAudit::getLoginStatus, "SUCCESS").ge(SysLoginAudit::getLoginTime, today));
        Long failed = auditMapper.selectCount(new LambdaQueryWrapper<SysLoginAudit>()
                .eq(SysLoginAudit::getLoginStatus, "FAILED").ge(SysLoginAudit::getLoginTime, today));
        Long locked = auditMapper.selectCount(new LambdaQueryWrapper<SysLoginAudit>()
                .eq(SysLoginAudit::getLoginStatus, "LOCKED").ge(SysLoginAudit::getLoginTime, today));
        Long total = auditMapper.selectCount(null);
        return Map.of(
                "todaySuccess", success,
                "todayFailed", failed,
                "todayLocked", locked,
                "total", total
        );
    }

    /** 最近 7 天每天登录次数 (按天) */
    public List<Map<String, Object>> trend(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days).withHour(0).withMinute(0).withSecond(0);
        List<SysLoginAudit> all = auditMapper.selectList(
                new LambdaQueryWrapper<SysLoginAudit>().ge(SysLoginAudit::getLoginTime, from));
        return all.stream()
                .collect(Collectors.groupingBy(a -> a.getLoginTime().toLocalDate().toString()))
                .entrySet().stream()
                .map(e -> Map.<String, Object>of("date", e.getKey(), "count", (long) e.getValue().size()))
                .sorted((x, y) -> ((String) x.get("date")).compareTo((String) y.get("date")))
                .collect(Collectors.toList());
    }
}
