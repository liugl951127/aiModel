package com.aiplatform.system.service;

import com.aiplatform.system.entity.SysMenu;
import com.aiplatform.system.entity.SysRoleMenu;
import com.aiplatform.system.mapper.SysMenuMapper;
import com.aiplatform.system.mapper.SysRoleMenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public List<SysMenu> list() {
        return menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSortOrder));
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
        if (menu.getTenantId() == null) menu.setTenantId(1L);
        menuMapper.insert(menu);
        return menu;
    }

    public SysMenu update(SysMenu menu) {
        menuMapper.updateById(menu);
        return menu;
    }

    public void delete(Long id) {
        menuMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
    }

    /** 角色对应的菜单 id 列表 */
    public List<Long> listMenuIdsByRole(Long roleId) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
    }

    /** 给角色分配菜单 (覆盖式) */
    @Transactional
    public void assignToRole(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) return;
        for (Long mid : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(mid);
            roleMenuMapper.insert(rm);
        }
    }
}
