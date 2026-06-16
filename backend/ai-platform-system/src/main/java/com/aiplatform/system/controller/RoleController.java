package com.aiplatform.system.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.system.entity.SysRole;
import com.aiplatform.system.service.RoleService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;

    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.success(roleService.list());
    }

    @GetMapping("/page")
    public Result<IPage<SysRole>> page(@RequestParam(defaultValue = "1") int current,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String keyword) {
        return Result.success(roleService.page(current, size, keyword));
    }

    @PostMapping
    public Result<SysRole> create(@RequestBody SysRole role) {
        return Result.success(roleService.create(role));
    }

    @PutMapping
    public Result<SysRole> update(@RequestBody SysRole role) {
        return Result.success(roleService.update(role));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/status/{status}")
    public Result<Void> changeStatus(@PathVariable Long id, @PathVariable Integer status) {
        roleService.changeStatus(id, status);
        return Result.success();
    }

    @GetMapping("/stats")
    public Result<List<Map<String, Object>>> stats() {
        return Result.success(roleService.stats());
    }

    /**
     * 给用户分配角色 (覆盖式).
     * body: { userId, tenantId, roleIds: [1, 2, 3] }
     */
    @PostMapping("/assign")
    public Result<Void> assign(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        Long tenantId = body.get("tenantId") == null ? null : ((Number) body.get("tenantId")).longValue();
        @SuppressWarnings("unchecked")
        List<Number> rids = (List<Number>) body.get("roleIds");
        List<Long> roleIds = rids == null ? List.of() : rids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
        roleService.assignToUser(userId, tenantId, roleIds);
        return Result.success();
    }

    @GetMapping("/by-user/{userId}")
    public Result<List<SysRole>> listByUser(@PathVariable Long userId,
                                            @RequestParam(required = false) Long tenantId) {
        return Result.success(roleService.listByUser(userId, tenantId));
    }

    @GetMapping("/{id}/users")
    public Result<List<Long>> listUserIdsByRole(@PathVariable Long id) {
        return Result.success(roleService.listUserIdsByRole(id));
    }
}
