package com.aiplatform.system.service;

import com.aiplatform.redis.distributed.DistributedCache;
import com.aiplatform.system.entity.SysMenu;
import com.aiplatform.system.entity.SysRoleMenu;
import com.aiplatform.system.mapper.SysMenuMapper;
import com.aiplatform.system.mapper.SysRoleMenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ★ v3.x 性能优化: 菜单高频读加 Redis 缓存.
 * <ul>
 *   <li>list() - 菜单全量 (后端管理页面渲染)</li>
 *   <li>tree() - 菜单树 (前端菜单渲染, 每次登录 + 路由跳转都查)</li>
 * </ul>
 * 写操作 (create/update/delete/assignToRole) 主动清缓存.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    /** 菜单缓存 key + ttl (10 分钟, 菜单不常变) */
    private static final String CACHE_KEY_LIST = "aiplatform:menu:list";
    private static final String CACHE_KEY_TREE = "aiplatform:menu:tree";
    private static final String CACHE_KEY_PREFIX_ROLE = "aiplatform:menu:role:";
    private static final int CACHE_TTL_SEC = 600;

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final DistributedCache cache;

    public List<SysMenu> list() {
        return cache.getOrLoadJson(CACHE_KEY_LIST, CACHE_TTL_SEC,
            () -> menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSortOrder)),
            new TypeReference<List<SysMenu>>() {});
    }

    public List<SysMenu> tree() {
        List<SysMenu> all = list();
        Map<Long, List<SysMenu>> byParent = all.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));
        List<SysMenu> roots = byParent.getOrDefault(0L, new ArrayList<>());
        roots.forEach(r -> fillChildren(r, byParent));
        return roots;
    }

    private void fillChildren(SysMenu parent, Map<Long, List<SysMenu>> byParent) {
        List<SysMenu> children = byParent.get(parent.getId());
        if (children != null) {
            children.forEach(c -> fillChildren(c, byParent));
        }
    }

    public SysMenu create(SysMenu menu) {
        if (menu.getStatus() == null) menu.setStatus(1);
        if (menu.getVisible() == null) menu.setVisible(1);
        if (menu.getMenuType() == null) menu.setMenuType(1);
        if (menu.getParentId() == null) menu.setParentId(0L);
        if (menu.getSortOrder() == null) menu.setSortOrder(0);
        menuMapper.insert(menu);
        invalidateCache();
        return menu;
    }

    public SysMenu update(SysMenu menu) {
        menuMapper.updateById(menu);
        invalidateCache();
        return menu;
    }

    public void delete(Long id) {
        menuMapper.deleteById(id);
        invalidateCache();
    }

    public List<Long> listMenuIdsByRole(Long roleId) {
        String key = CACHE_KEY_PREFIX_ROLE + roleId;
        return cache.getOrLoadJson(key, CACHE_TTL_SEC,
            () -> roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                    .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList()),
            new TypeReference<List<Long>>() {});
    }

    @Transactional
    public void assignToRole(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null && !menuIds.isEmpty()) {
            for (Long menuId : menuIds) {
                roleMenuMapper.insert(SysRoleMenu.builder().roleId(roleId).menuId(menuId).build());
            }
        }
        invalidateCache();
        cache.invalidate(CACHE_KEY_PREFIX_ROLE + roleId);
    }

    /** 清菜单缓存 */
    private void invalidateCache() {
        cache.invalidate(CACHE_KEY_LIST);
        cache.invalidate(CACHE_KEY_TREE);
    }
}