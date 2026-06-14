package com.aiplatform.agent.service;

import com.aiplatform.agent.entity.MultiAgentCase;
import com.aiplatform.agent.mapper.MultiAgentCaseMapper;
import com.aiplatform.common.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 多智能体案例服务。
 * <p>负责按 caseKey 索引、按 domain 过滤、推荐位（featured）排序等。
 * 案例数据通过 {@link MultiAgentCaseSeeder} 在应用启动时写入（idempotent）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentCaseService {

    private final MultiAgentCaseMapper mapper;

    /**
     * 列出所有案例，featured 在前。
     */
    public List<MultiAgentCase> listAll() {
        return withTenant(0L, () -> mapper.selectList(new LambdaQueryWrapper<MultiAgentCase>()
                .orderByDesc(MultiAgentCase::getFeatured)
                .orderByDesc(MultiAgentCase::getId)));
    }

    /**
     * 按业务域过滤。
     */
    public List<MultiAgentCase> listByDomain(String domain) {
        return withTenant(0L, () -> mapper.selectList(new LambdaQueryWrapper<MultiAgentCase>()
                .eq(domain != null && !domain.isBlank(), MultiAgentCase::getDomain, domain)
                .orderByDesc(MultiAgentCase::getFeatured)
                .orderByDesc(MultiAgentCase::getId)));
    }

    /**
     * 按 caseKey 取单条，幂等。
     */
    public MultiAgentCase getByKey(String caseKey) {
        return withTenant(0L, () -> mapper.selectOne(new LambdaQueryWrapper<MultiAgentCase>()
                .eq(MultiAgentCase::getCaseKey, caseKey)));
    }

    /**
     * Upsert：按 caseKey 查重；存在则更新，不存在则插入。
     */
    public MultiAgentCase upsert(MultiAgentCase c) {
        return withTenant(0L, () -> {
            MultiAgentCase existing = getByKey(c.getCaseKey());
            if (existing == null) {
                mapper.insert(c);
                log.info("[CASE] inserted {}", c.getCaseKey());
                return c;
            }
            c.setId(existing.getId());
            mapper.updateById(c);
            log.info("[CASE] updated {} (id={})", c.getCaseKey(), existing.getId());
            return c;
        });
    }

    /**
     * 在 demo 模式里走默认租户 0。多租户拦截器会拼 {@code AND tenant_id = 0}，
     * 与表里 0 匹配。
     */
    private static <T> T withTenant(long tenantId, java.util.function.Supplier<T> body) {
        Long prev = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantId);
        try {
            return body.get();
        } finally {
            if (prev == null) TenantContext.clear();
            else TenantContext.setTenantId(prev);
        }
    }
}
