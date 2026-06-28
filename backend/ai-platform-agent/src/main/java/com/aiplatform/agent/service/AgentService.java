package com.aiplatform.agent.service;

import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.starter.mybatis.support.PageAdapter;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.agent.entity.AgentEntity;
import com.aiplatform.agent.mapper.AgentMapper;
import com.aiplatform.redis.distributed.DistributedCache;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * ★ v3.x 性能优化: Agent 高频读加缓存.
 * <ul>
 *   <li>list() - 智能体列表 (Dashboard/Agent 管理页面高频调用)</li>
 *   <li>getById() - 单个智能体详情 (每次 chat 都查)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final String CACHE_KEY_LIST = "aiplatform:agent:list";
    private static final String CACHE_KEY_PREFIX = "aiplatform:agent:";
    private static final int CACHE_TTL_SEC = 300;

    private final AgentMapper agentMapper;
    private final DistributedCache cache;

    public AgentEntity create(AgentEntity agent) {
        if (agent.getAgentCode() == null) {
            agent.setAgentCode("A-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (agent.getStatus() == null) agent.setStatus(1);
        if (agent.getTemperature() == null) agent.setTemperature(0.7);
        if (agent.getMaxSteps() == null) agent.setMaxSteps(5);
        agentMapper.insert(agent);
        invalidateCache();
        return agent;
    }

    public AgentEntity update(AgentEntity agent) {
        if (agent.getId() == null) throw new BusinessException(ResultCode.BAD_REQUEST, "ID 必填");
        agentMapper.updateById(agent);
        invalidateCache(agent.getId());
        return agentMapper.selectById(agent.getId());
    }

    public void delete(Long id) {
        agentMapper.deleteById(id);
        invalidateCache(id);
    }

    public AgentEntity getById(Long id) {
        AgentEntity a = cache.getOrLoad(CACHE_KEY_PREFIX + id, CACHE_TTL_SEC,
            () -> agentMapper.selectById(id), AgentEntity.class);
        if (a == null) throw new BusinessException(ResultCode.NOT_FOUND, "智能体不存在");
        return a;
    }

    public List<AgentEntity> list() {
        return cache.getOrLoadJson(CACHE_KEY_LIST, CACHE_TTL_SEC,
            () -> agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                .orderByDesc(AgentEntity::getCreateTime)),
            new TypeReference<List<AgentEntity>>() {});
    }

    public PageResult<AgentEntity> page(PageQuery q) {
        // 分页不缓存
        Page<AgentEntity> p = q.toPage();
        LambdaQueryWrapper<AgentEntity> w = new LambdaQueryWrapper<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            w.like(AgentEntity::getAgentName, q.getKeyword())
                    .or().like(AgentEntity::getAgentCode, q.getKeyword());
        }
        w.orderByDesc(AgentEntity::getCreateTime);
        return PageAdapter.of(agentMapper.selectPage(p, w));
    }

    private void invalidateCache() {
        cache.invalidate(CACHE_KEY_LIST);
    }

    private void invalidateCache(Long id) {
        cache.invalidate(CACHE_KEY_PREFIX + id);
        cache.invalidate(CACHE_KEY_LIST);
    }
}