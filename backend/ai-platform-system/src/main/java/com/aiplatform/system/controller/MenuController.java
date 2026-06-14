package com.aiplatform.system.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.system.entity.SysMenu;
import com.aiplatform.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
