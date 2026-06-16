package com.aiplatform.user.controller;

import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.common.result.Result;
import com.aiplatform.user.entity.SysUser;
import com.aiplatform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/feign/by-username")
    public Result<java.util.Map<String, Object>> getByUsername(@RequestParam String username) {
        return Result.success(userService.getByUsername(username));
    }

    /**
     * feign 端点: 接收 Map 创建用户 (用于 auth-service 注册流程, 避免依赖实体序列化).
     * 返回 Map { id, username, tenantId } 方便 caller 使用.
     */
    @PostMapping("/feign/create")
    public Result<java.util.Map<String, Object>> feignCreate(@RequestBody java.util.Map<String, Object> body) {
        SysUser user = new SysUser();
        user.setUsername((String) body.get("username"));
        user.setPassword((String) body.get("password"));
        user.setNickname((String) body.get("nickname"));
        user.setPhone((String) body.get("phone"));
        user.setEmail((String) body.get("email"));
        user.setDepartment((String) body.get("department"));
        Object status = body.get("status");
        user.setStatus(status == null ? 1 : Integer.parseInt(status.toString()));
        SysUser created = userService.create(user);
        // 绑定公司
        Object tenantIdObj = body.get("tenantId");
        if (tenantIdObj != null) {
            Long tenantId = Long.parseLong(tenantIdObj.toString());
            try {
                userService.bindTenant(created.getId(), tenantId);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(UserController.class).warn("绑定公司失败: userId={}, tenantId={}, err={}", created.getId(), tenantId, e.getMessage());
            }
        }
        java.util.Map<String, Object> ret = new java.util.HashMap<>();
        ret.put("id", created.getId());
        ret.put("username", created.getUsername());
        ret.put("tenantId", tenantIdObj);
        return Result.success(ret);
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

    /** 重置密码为默认 123456 (admin 操作) */
    @PostMapping("/{id}/reset-password")
    public Result<String> resetPassword(@PathVariable Long id) {
        String pwd = userService.resetPassword(id);
        return Result.success(pwd);
    }

    /** 改自己的密码: { oldPwd, newPwd } */
    @PostMapping("/{id}/change-password")
    public Result<Void> changePassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.changePassword(id, body.get("oldPwd"), body.get("newPwd"));
        return Result.success();
    }

    /** 启停账号 */
    @PostMapping("/{id}/status/{status}")
    public Result<Void> changeStatus(@PathVariable Long id, @PathVariable Integer status) {
        userService.changeStatus(id, status);
        return Result.success();
    }

    /** 用户统计 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(userService.stats());
    }
}
