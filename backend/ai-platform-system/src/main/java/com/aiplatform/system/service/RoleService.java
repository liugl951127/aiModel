package com.aiplatform.system.service;

import com.aiplatform.system.entity.SysRole;
import com.aiplatform.system.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final SysRoleMapper roleMapper;

    public List<SysRole> list() {
        return roleMapper.selectList(null);
    }

    public SysRole create(SysRole role) {
        if (role.getStatus() == null) role.setStatus(1);
        roleMapper.insert(role);
        return role;
    }
}
