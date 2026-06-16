package com.aiplatform.system.service;

import com.aiplatform.system.entity.SysRole;
import com.aiplatform.system.entity.SysUserRole;
import com.aiplatform.system.mapper.SysRoleMapper;
import com.aiplatform.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    public List<SysRole> list() {
        return roleMapper.selectList(null);
    }

    public IPage<SysRole> page(int current, int size, String keyword) {
        LambdaQueryWrapper<SysRole> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.like(SysRole::getRoleCode, keyword).or().like(SysRole::getRoleName, keyword);
        }
        q.orderByAsc(SysRole::getId);
        return roleMapper.selectPage(new Page<>(current, size), q);
    }

    public SysRole create(SysRole role) {
        if (role.getStatus() == null) role.setStatus(1);
        if (role.getTenantId() == null) role.setTenantId(1L);
        roleMapper.insert(role);
        return role;
    }

    public SysRole update(SysRole role) {
        roleMapper.updateById(role);
        return role;
    }

    public void delete(Long id) {
        roleMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
    }

    public void changeStatus(Long id, Integer status) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setStatus(status);
        roleMapper.updateById(r);
    }

    /**
     * 给一个用户分配多个角色 (覆盖式).
     * 在 sys_user_role 表里删旧 + 插新.
     */
    @Transactional
    public void assignToUser(Long userId, Long tenantId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) return;
        for (Long rid : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(rid);
            ur.setTenantId(tenantId == null ? 1L : tenantId);
            userRoleMapper.insert(ur);
        }
    }

    /**
     * 拿到一个用户的所有角色.
     */
    public List<SysRole> listByUser(Long userId, Long tenantId) {
        List<SysUserRole> urs = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(tenantId != null, SysUserRole::getTenantId, tenantId));
        if (urs.isEmpty()) return List.of();
        List<Long> ids = urs.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        return roleMapper.selectBatchIds(ids);
    }

    /**
     * 反向: 一个角色下的所有用户 id.
     */
    public List<Long> listUserIdsByRole(Long roleId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId))
                .stream().map(SysUserRole::getUserId).collect(Collectors.toList());
    }

    /**
     * 角色统计: 含 (角色名, 用户数).
     */
    public List<Map<String, Object>> stats() {
        List<SysRole> roles = roleMapper.selectList(null);
        return roles.stream().map(r -> {
            int userCount = userRoleMapper.selectCount(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, r.getId())).intValue();
            return Map.<String, Object>of(
                    "id", r.getId(),
                    "roleCode", r.getRoleCode(),
                    "roleName", r.getRoleName(),
                    "userCount", userCount,
                    "status", r.getStatus());
        }).collect(Collectors.toList());
    }
}
