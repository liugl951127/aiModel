package com.aiplatform.agent.service;

import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.starter.mybatis.support.PageAdapter;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.agent.entity.AgentEntity;
import com.aiplatform.agent.mapper.AgentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentMapper agentMapper;

    public AgentEntity create(AgentEntity agent) {
        if (agent.getAgentCode() == null) {
            agent.setAgentCode("A-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (agent.getStatus() == null) agent.setStatus(1);
        if (agent.getTemperature() == null) agent.setTemperature(0.7);
        if (agent.getMaxSteps() == null) agent.setMaxSteps(5);
        agentMapper.insert(agent);
        return agent;
    }

    public AgentEntity update(AgentEntity agent) {
        if (agent.getId() == null) throw new BusinessException(ResultCode.BAD_REQUEST, "ID 必填");
        agentMapper.updateById(agent);
        return agentMapper.selectById(agent.getId());
    }

    public void delete(Long id) {
        agentMapper.deleteById(id);
    }

    public AgentEntity getById(Long id) {
        AgentEntity a = agentMapper.selectById(id);
        if (a == null) throw new BusinessException(ResultCode.NOT_FOUND, "智能体不存在");
        return a;
    }

    public List<AgentEntity> list() {
        return agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                .orderByDesc(AgentEntity::getCreateTime));
    }

    public PageResult<AgentEntity> page(PageQuery q) {
        Page<AgentEntity> p = q.toPage();
        LambdaQueryWrapper<AgentEntity> w = new LambdaQueryWrapper<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            w.like(AgentEntity::getAgentName, q.getKeyword())
                    .or().like(AgentEntity::getAgentCode, q.getKeyword());
        }
        w.orderByDesc(AgentEntity::getCreateTime);
        return PageAdapter.of(agentMapper.selectPage(p, w));
    }
}
