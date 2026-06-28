package com.aiplatform.agent.service;

import com.aiplatform.agent.entity.ToolEntity;
import com.aiplatform.agent.mapper.ToolMapper;
import com.aiplatform.redis.distributed.DistributedCache;
import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.starter.mybatis.support.PageAdapter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * ★ v3.x 性能优化: Tool 列表缓存 (Agent 调用工具时高频读).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

    private static final String CACHE_KEY_LIST = "aiplatform:tool:list";
    private static final int CACHE_TTL_SEC = 600;

    private final ToolMapper toolMapper;
    private final DistributedCache cache;

    public ToolEntity create(ToolEntity tool) {
        if (tool.getToolCode() == null) {
            tool.setToolCode("T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (tool.getStatus() == null) tool.setStatus(1);
        toolMapper.insert(tool);
        cache.invalidate(CACHE_KEY_LIST);
        return tool;
    }

    public List<ToolEntity> list() {
        return cache.getOrLoadJson(CACHE_KEY_LIST, CACHE_TTL_SEC,
            () -> toolMapper.selectList(new LambdaQueryWrapper<ToolEntity>()
                .orderByDesc(ToolEntity::getCreateTime)),
            new TypeReference<List<ToolEntity>>() {});
    }

    public PageResult<ToolEntity> page(PageQuery q) {
        Page<ToolEntity> p = q.toPage();
        LambdaQueryWrapper<ToolEntity> w = new LambdaQueryWrapper<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            w.like(ToolEntity::getToolName, q.getKeyword());
        }
        w.orderByDesc(ToolEntity::getCreateTime);
        return PageAdapter.of(toolMapper.selectPage(p, w));
    }

    public void delete(Long id) {
        toolMapper.deleteById(id);
        cache.invalidate(CACHE_KEY_LIST);
    }
}