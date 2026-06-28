package com.aiplatform.system.service;

import com.aiplatform.redis.distributed.DistributedCache;
import com.aiplatform.system.entity.SysRole;
import com.aiplatform.system.entity.SysUserRole;
import com.aiplatform.system.mapper.SysRoleMapper;
import com.aiplatform.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ★ v3.x 性能优化: Role 高频读加 Redis 缓存.
 * <ul>
 *   <li>list() - 角色全量 (后端管理)</li>
 *   <li>listByUser() - 用户角色 (JWT 鉴权高频, 每个请求都查)</li>
 *   <li>listUserIdsByRole() - 角色下的用户</li>
 * </ul>
 * 写操作自动 invalidate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final String CACHE_KEY_LIST = "aiplatform:role:list";
    private static final String CACHE_KEY_PREFIX_USER = "aiplatform:role:user:";
    private static final String CACHE_KEY_PREFIX_ROLE_USERS = "aiplatform:role:role-users:";
    private static final int CACHE_TTL_SEC = 300;

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final DistributedCache cache;

    public List<SysRole> list() {
        return cache.getOrLoadJson(CACHE_KEY_LIST, CACHE_TTL_SEC,
            () -> roleMapper.selectList(null),
            new TypeReference<List<SysRole>>() {});
    }

    public IPage<SysRole> page(int current, int size, String keyword) {
        // 分页不缓存
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
        invalidateCache();
        return role;
    }

    public SysRole update(SysRole role) {
        roleMapper.updateById(role);
        invalidateCache();
        return role;
    }

    public void delete(Long id) {
        roleMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        invalidateCache();
    }

    public void changeStatus(Long id, Integer status) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setStatus(status);
        roleMapper.updateById(r);
        invalidateCache();
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
        // 清用户角色缓存
        cache.invalidate(CACHE_KEY_PREFIX_USER + userId + ":" + (tenantId == null ? 0L : tenantId));
    }

    /**
     * 拿到一个用户的所有角色 — 鉴权高频读, 必缓存.
     */
    public List<SysRole> listByUser(Long userId, Long tenantId) {
        String key = CACHE_KEY_PREFIX_USER + userId + ":" + (tenantId == null ? 0L : tenantId);
        return cache.getOrLoadJson(key, CACHE_TTL_SEC, () -> {
            List<SysUserRole> urs = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getUserId, userId)
                            .eq(tenantId != null, SysUserRole::getTenantId, tenantId));
            if (urs.isEmpty()) return List.<SysRole>of();
            List<Long> ids = urs.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
            return roleMapper.selectBatchIds(ids);
        }, new TypeReference<List<SysRole>>() {});
    }

    /**
     * 反向: 一个角色下的所有用户 id.
     */
    public List<Long> listUserIdsByRole(Long roleId) {
        String key = CACHE_KEY_PREFIX_ROLE_USERS + roleId;
        return cache.getOrLoadJson(key, CACHE_TTL_SEC,
            () -> userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId))
                .stream().map(SysUserRole::getUserId).collect(Collectors.toList()),
            new TypeReference<List<Long>>() {});
    }

    public List<Map<String, Object>> stats() {
        // 统计不缓存 (业务方调用频率低, 数据需实时)
        long total = roleMapper.selectCount(null);
        long enabled = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, 1));
        return List.of(
                Map.of("label", "总角色", "value", total),
                Map.of("label", "启用", "value", enabled),
                Map.of("label", "禁用", "value", total - enabled)
        );
    }

    private void invalidateCache() {
        cache.invalidate(CACHE_KEY_LIST);
        // 用户角色缓存 key 不定, 用 pattern 清
        cache.evictByPattern(CACHE_KEY_PREFIX_USER + "*");
    }
}