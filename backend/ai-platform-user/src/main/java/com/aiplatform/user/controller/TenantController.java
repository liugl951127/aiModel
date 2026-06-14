package com.aiplatform.user.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.user.entity.SysTenant;
import com.aiplatform.user.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

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
}
