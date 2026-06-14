package com.aiplatform.system.service;

import com.aiplatform.system.entity.SysMenu;
import com.aiplatform.system.mapper.SysMenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysMenuMapper menuMapper;

    public List<SysMenu> tree() {
        List<SysMenu> all = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSortOrder));
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
}
