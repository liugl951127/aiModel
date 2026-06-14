package com.aiplatform.system.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.system.entity.SysRole;
import com.aiplatform.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;

    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.success(roleService.list());
    }

    @PostMapping
    public Result<SysRole> create(@RequestBody SysRole role) {
        return Result.success(roleService.create(role));
    }
}
