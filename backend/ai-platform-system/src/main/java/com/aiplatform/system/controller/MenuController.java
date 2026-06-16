package com.aiplatform.system.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.system.entity.SysMenu;
import com.aiplatform.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {
    private final MenuService menuService;

    @GetMapping("/tree")
    public Result<List<SysMenu>> tree() {
        return Result.success(menuService.tree());
    }

    @GetMapping("/list")
    public Result<List<SysMenu>> list() {
        return Result.success(menuService.list());
    }

    @PostMapping
    public Result<SysMenu> create(@RequestBody SysMenu menu) {
        return Result.success(menuService.create(menu));
    }

    @PutMapping
    public Result<SysMenu> update(@RequestBody SysMenu menu) {
        return Result.success(menuService.update(menu));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success();
    }

    @GetMapping("/by-role/{roleId}")
    public Result<List<Long>> listMenuIdsByRole(@PathVariable Long roleId) {
        return Result.success(menuService.listMenuIdsByRole(roleId));
    }

    @PostMapping("/assign")
    public Result<Void> assignToRole(@RequestBody java.util.Map<String, Object> body) {
        Long roleId = ((Number) body.get("roleId")).longValue();
        @SuppressWarnings("unchecked")
        List<Number> mids = (List<Number>) body.get("menuIds");
        List<Long> menuIds = mids == null ? List.of() : mids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
        menuService.assignToRole(roleId, menuIds);
        return Result.success();
    }
}
