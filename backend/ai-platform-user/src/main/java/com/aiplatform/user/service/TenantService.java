package com.aiplatform.user.service;

import com.aiplatform.user.entity.SysTenant;
import com.aiplatform.user.mapper.SysTenantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final SysTenantMapper tenantMapper;

    public List<SysTenant> listAll() {
        return tenantMapper.selectList(null);
    }

    public SysTenant create(SysTenant tenant) {
        if (tenant.getStatus() == null) {
            tenant.setStatus(1);
        }
        tenantMapper.insert(tenant);
        return tenant;
    }

    public SysTenant getByCode(String code) {
        return tenantMapper.selectOne(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getTenantCode, code)
                .last("limit 1"));
    }

    /**
     * 按主键 ID 查租户.
     */
    public SysTenant getById(Long id) {
        if (id == null) return null;
        return tenantMapper.selectById(id);
    }
}
