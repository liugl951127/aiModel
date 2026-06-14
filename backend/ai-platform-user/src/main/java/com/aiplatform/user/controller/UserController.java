package com.aiplatform.user.controller;

import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.common.result.Result;
import com.aiplatform.user.entity.SysUser;
import com.aiplatform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/feign/by-username")
    public Result<java.util.Map<String, Object>> getByUsername(@RequestParam String username) {
        return Result.success(userService.getByUsername(username));
    }

    @GetMapping("/page")
    public PageResult<SysUser> page(PageQuery query) {
        return userService.page(query);
    }

    @GetMapping("/list")
    public Result<List<SysUser>> list() {
        return Result.success(userService.listAll());
    }

    @PostMapping
    public Result<SysUser> create(@RequestBody SysUser user) {
        return Result.success(userService.create(user));
    }

    @PutMapping
    public Result<SysUser> update(@RequestBody SysUser user) {
        return Result.success(userService.update(user));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }
}
