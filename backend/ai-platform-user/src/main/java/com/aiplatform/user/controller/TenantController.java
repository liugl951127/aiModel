package com.aiplatform.user.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.user.entity.SysTenant;
import com.aiplatform.user.entity.SysUser;
import com.aiplatform.user.mapper.SysTenantMapper;
import com.aiplatform.user.mapper.SysUserMapper;
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

    @GetMapping("/list")
    public Result<List<SysTenant>> list() {
        return Result.success(tenantService.listAll());
    }

    @GetMapping("/{code}")
    public Result<SysTenant> get(@PathVariable String code) {
        return Result.success(tenantService.getByCode(code));
    }

    @PostMapping
    public Result<SysTenant> create(@RequestBody SysTenant tenant) {
        return Result.success(tenantService.create(tenant));
    }

    /**
     * Feign 端点：按用户名返回他所属的所有公司（租户）。
     *
     * <p>登录前置调用，结果含 {@code id / tenantCode / tenantName / role / department}。
     * 一个用户可以属于多个公司（咨询/外包等场景），前端弹出公司选择器。</p>
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
        // 该用户默认 tenant_id 是主公司；同时从 sys_tenant 表拉所有 active 的，
        // 让用户可以切换"体验"其它公司（只对 admin 角色开放）
        List<SysTenant> all = tenantMapper.selectList(
                new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getStatus, 1)
                        .orderByAsc(SysTenant::getId));
        // 主公司排第一
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (SysTenant t : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("tenantCode", t.getTenantCode());
            m.put("tenantName", t.getTenantName());
            m.put("role", t.getId().equals(user.getTenantId()) ? "owner" : "guest");
            m.put("department", user.getDepartment());
            out.add(m);
        }
        return Result.success(out);
    }
}
