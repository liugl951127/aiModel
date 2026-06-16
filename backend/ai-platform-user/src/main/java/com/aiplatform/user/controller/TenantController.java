package com.aiplatform.user.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.user.entity.SysTenant;
import com.aiplatform.user.entity.SysUser;
import com.aiplatform.user.entity.SysUserTenant;
import com.aiplatform.user.mapper.SysTenantMapper;
import com.aiplatform.user.mapper.SysUserMapper;
import com.aiplatform.user.mapper.SysUserTenantMapper;
import com.aiplatform.user.service.TenantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final SysUserTenantMapper userTenantMapper;

    @GetMapping("/list")
    public Result<List<SysTenant>> list() {
        return Result.success(tenantService.listAll());
    }

    @GetMapping("/page")
    public Result<com.baomidou.mybatisplus.core.metadata.IPage<SysTenant>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysTenant> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.like(SysTenant::getTenantCode, keyword).or().like(SysTenant::getTenantName, keyword);
        }
        q.orderByAsc(SysTenant::getId);
        return Result.success(tenantMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), q));
    }

    @GetMapping("/{code}")
    public Result<SysTenant> get(@PathVariable String code) {
        return Result.success(tenantService.getByCode(code));
    }

    @PostMapping
    public Result<SysTenant> create(@RequestBody SysTenant tenant) {
        return Result.success(tenantService.create(tenant));
    }

    @PutMapping
    public Result<SysTenant> update(@RequestBody SysTenant tenant) {
        tenantMapper.updateById(tenant);
        return Result.success(tenantMapper.selectById(tenant.getId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tenantMapper.deleteById(id);
        userTenantMapper.delete(new LambdaQueryWrapper<SysUserTenant>().eq(SysUserTenant::getTenantId, id));
        return Result.success();
    }

    @PostMapping("/{id}/status/{status}")
    public Result<Void> changeStatus(@PathVariable Long id, @PathVariable Integer status) {
        SysTenant t = new SysTenant();
        t.setId(id);
        t.setStatus(status);
        tenantMapper.updateById(t);
        return Result.success();
    }

    /**
     * Feign 端点：按 ID 查租户详情.
     */
    @GetMapping("/feign/{id}")
    public Result<Map<String, Object>> feignById(@PathVariable Long id) {
        SysTenant t = tenantService.getById(id);
        if (t == null) {
            return Result.success(null);
        }
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", t.getId());
        map.put("tenantCode", t.getTenantCode());
        map.put("tenantName", t.getTenantName());
        map.put("status", t.getStatus());
        return Result.success(map);
    }

    /**
     * Feign 端点：按用户名返回他所属的所有公司（租户）。
     */
    @GetMapping("/feign/by-username")
    public Result<List<Map<String, Object>>> listByUsername(@RequestParam String username) {
        if (username == null || username.isBlank()) {
            return Result.success(List.of());
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username).last("limit 1"));
        if (user == null) {
            return Result.success(List.of());
        }
        // 优先从 sys_user_tenant 拿 (多对多)，没有就 fallback 到主公司
        List<SysUserTenant> uts = userTenantMapper.selectList(
                new LambdaQueryWrapper<SysUserTenant>().eq(SysUserTenant::getUserId, user.getId()));
        List<SysTenant> all = tenantMapper.selectList(
                new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getStatus, 1)
                        .orderByAsc(SysTenant::getId));
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        if (!uts.isEmpty()) {
            for (SysUserTenant ut : uts) {
                SysTenant t = tenantMapper.selectById(ut.getTenantId());
                if (t == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.getId());
                m.put("tenantCode", t.getTenantCode());
                m.put("tenantName", t.getTenantName());
                m.put("role", ut.getRoleInTenant());
                m.put("isDefault", ut.getIsDefault());
                m.put("department", user.getDepartment());
                out.add(m);
            }
        } else {
            // fallback: 主公司
            SysTenant t = tenantMapper.selectById(user.getTenantId());
            if (t != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.getId());
                m.put("tenantCode", t.getTenantCode());
                m.put("tenantName", t.getTenantName());
                m.put("role", "owner");
                m.put("isDefault", 1);
                m.put("department", user.getDepartment());
                out.add(m);
            }
            // admin 看所有
            if ("admin".equalsIgnoreCase(user.getUsername())) {
                for (SysTenant at : all) {
                    if (out.stream().noneMatch(x -> ((Number) x.get("id")).longValue() == at.getId())) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", at.getId());
                        m.put("tenantCode", at.getTenantCode());
                        m.put("tenantName", at.getTenantName());
                        m.put("role", "super");
                        m.put("isDefault", 0);
                        m.put("department", user.getDepartment());
                        out.add(m);
                    }
                }
            }
        }
        return Result.success(out);
    }

    /** 租户统计 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Long total = tenantMapper.selectCount(null);
        Long active = tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getStatus, 1));
        Long userCount = userTenantMapper.selectCount(null);
        return Result.success(Map.of("total", total, "active", active, "userBindings", userCount));
    }

    /** 一个公司下所有用户 */
    @GetMapping("/{id}/users")
    public Result<List<Map<String, Object>>> listUsers(@PathVariable Long id) {
        List<SysUserTenant> uts = userTenantMapper.selectList(
                new LambdaQueryWrapper<SysUserTenant>().eq(SysUserTenant::getTenantId, id));
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (SysUserTenant ut : uts) {
            SysUser u = userMapper.selectById(ut.getUserId());
            if (u == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("department", u.getDepartment());
            m.put("roleInTenant", ut.getRoleInTenant());
            m.put("isDefault", ut.getIsDefault());
            out.add(m);
        }
        return Result.success(out);
    }
}
